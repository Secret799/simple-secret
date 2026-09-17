package com.ss.gb28181;

import com.ss.gb28181.internal.GbSdp;
import com.ss.gb28181.internal.GbMansRtsp;
import com.ss.gb28181.internal.GbXml;
import com.ss.gb28181.internal.ResponseSourceGuard;
import gov.nist.javax.sip.ResponseEventExt;
import gov.nist.javax.sip.address.AddressFactoryImpl;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.message.MessageFactoryImpl;
import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/** Direct-device dialogs; all state is protected by the owning server's monitor. */
final class GbPlayback {
    private static final long CLEANUP_NANOS = TimeUnit.SECONDS.toNanos(32); // RFC 3261 64*T1, T1=500ms.
    private final Object lock;
    private final GbServerOptions options;
    private final Map<String, SipProvider> providers;
    private final ResponseSourceGuard responseGuard;
    private final HeaderFactoryImpl headers = new HeaderFactoryImpl();
    private final AddressFactoryImpl addresses = new AddressFactoryImpl();
    private final MessageFactoryImpl messages = new MessageFactoryImpl();
    private final Map<String, Call> calls = new HashMap<>();
    private int nextSsrc;
    private boolean closed;

    GbPlayback(Object lock, GbServerOptions options, Map<String, SipProvider> providers, ResponseSourceGuard responseGuard) {
        this.lock = lock; this.options = options; this.providers = providers;
        this.responseGuard = responseGuard;
    }

    CompletableFuture<GbPlaySession> play(String device, String channel, GbRtpTarget target, GbPlaybackRange range,
                                          GbDownloadRequest downloadRequest, Peer peer) {
        maintain();
        if (closed) throw new IllegalStateException("Playback server is closed");
        if (calls.size() >= options.maxPlaySessions()) throw new IllegalStateException("Playback session capacity exceeded");
        SipProvider provider = providers.get(peer.transport);
        String callId = provider.getNewCallId().getCallId();
        Call call = new Call(device, channel, target, range, downloadRequest, peer, callId, ssrc(range != null));
        calls.put(callId, call);
        try {
            Request invite = request(call, Request.INVITE, 1, null);
            invite.addHeader(headers.createSubjectHeader(channel + ":" + call.ssrc + "," + options.serverId() + ":" + call.ssrc));
            byte[] offer = downloadRequest == null ? GbSdp.offer(options.serverId(), channel, target, call.ssrc, range)
                    : GbSdp.downloadOffer(options.serverId(), channel, target, call.ssrc, downloadRequest);
            invite.setContent(offer,
                    headers.createContentTypeHeader("application", "sdp"));
            call.invite = provider.getNewClientTransaction(invite);
            call.invite.setApplicationData(callId);
            call.inviteBranch = ((ViaHeader) invite.getHeader(ViaHeader.NAME)).getBranch();
            expect(call, invite);
            call.invite.sendRequest();
        } catch (Exception e) {
            finish(call, new IllegalStateException("Cannot send playback INVITE", e));
        }
        return call.result;
    }

    boolean response(ResponseEvent event) {
        Response response = event.getResponse();
        CallIdHeader id = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        Call call = id == null ? null : calls.get(id.getCallId());
        if (call == null) return false;
        if (!(event instanceof ResponseEventExt ext) || !call.peer.matches(ext.getRemoteIpAddress(), ext.getRemotePort())
                || !call.peer.transport.equalsIgnoreCase(((SipProvider) event.getSource()).getListeningPoint().getTransport())) return true;
        FromHeader from = (FromHeader) response.getHeader(FromHeader.NAME);
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
        ViaHeader via = (ViaHeader) response.getHeader(ViaHeader.NAME);
        CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        if (from == null || to == null || via == null || cseq == null || !call.localTag.equals(from.getTag())) return true;
        String method = cseq.getMethod();
        int status = response.getStatusCode();
        try {
            if (Request.INVITE.equals(method)) {
                if (cseq.getSeqNumber() != 1 || !Objects.equals(call.inviteBranch, via.getBranch())) return true;
                if (status < 200) {
                    call.provisional = true;
                    if (abandoned(call)) abort(call, new CancellationException("Playback result abandoned"));
                    if (call.aborted) cancel(call);
                    return true;
                }
                call.inviteFinal = true;
                if (status >= 300) {
                    // RI owns ACK generation/retransmission for non-2xx INVITE finals.
                    if (call.remoteTag == null) finish(call, new IllegalStateException("Playback INVITE rejected: " + status));
                    return true;
                }
                String remoteTag = to.getTag();
                if (remoteTag == null || remoteTag.length() > 128) {
                    abort(call, new IllegalArgumentException("Missing or oversized playback dialog tag")); return true;
                }
                // Direct device profile has no SIP proxy forks. Never replace an established dialog's tag.
                if (call.remoteTag != null && !call.remoteTag.equals(remoteTag)) return true;
                call.remoteTag = remoteTag;
                providers.get(call.peer.transport).sendRequest(request(call, Request.ACK, 1, remoteTag));
                if (call.accepted) return true; // Every retransmitted 2xx is ACKed, never delivered twice.
                call.accepted = true;
                if (abandoned(call)) abort(call, new CancellationException("Playback result abandoned"));
                if (call.aborted || call.completion.isDone()) { bye(call); return true; }
                try {
                    ContentTypeHeader type = (ContentTypeHeader) response.getHeader(ContentTypeHeader.NAME);
                    if (type == null || !"application".equalsIgnoreCase(type.getContentType())
                            || !"sdp".equalsIgnoreCase(type.getContentSubType())
                            || response.getHeader(ContactHeader.NAME) == null
                            || response.getHeader(RecordRouteHeader.NAME) != null)
                        throw new IllegalArgumentException("Unsupported playback dialog response");
                    if (call.downloadRequest == null)
                        GbSdp.validateAnswer(response.getRawContent(), call.target, call.ssrc, options.maxMessageBytes(), call.range);
                    else call.downloadFileSize = GbSdp.validateDownloadAnswer(response.getRawContent(), call.target,
                            call.ssrc, options.maxMessageBytes(), call.downloadRequest);
                } catch (IllegalArgumentException e) { abort(call, e); return true; }
                call.established = true;
                GbPlaySession session = new GbPlaySession(call.device, call.channel, call.id, call.ssrc, call.target,
                        call.range, call.downloadRequest, call.downloadFileSize, call.completion,
                        () -> stop(call), command -> control(call, command));
                // If a caller cancelled/completed the public future during delivery, do not orphan the dialog.
                if (!call.result.complete(session)) abort(call, new CancellationException("Playback result abandoned"));
            } else if (Request.INFO.equals(method)) {
                controlResponse(call, response, cseq, via, to);
            } else if (Request.BYE.equals(method) && call.bye != null && cseq.getSeqNumber() == call.byeCseq
                    && Objects.equals(call.remoteTag, to.getTag())
                    && Objects.equals(((ViaHeader) call.bye.getRequest().getHeader(ViaHeader.NAME)).getBranch(), via.getBranch())
                    && status >= 200) {
                finish(call, status < 300 || status == 481 ? null : new IllegalStateException("Playback BYE rejected: " + status));
            }
        } catch (Exception e) { abort(call, new IllegalStateException("Playback signaling failed", e)); }
        return true;
    }

    boolean incomingBye(Request request, ServerTransaction tx, Peer peer) throws Exception {
        CallIdHeader id = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        Call call = id == null ? null : calls.get(id.getCallId());
        FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
        ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
        CSeqHeader cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
        int status = 481;
        if (call != null && call.peer.equals(peer) && call.remoteTag != null && from != null && to != null
                && call.remoteTag.equals(from.getTag()) && call.localTag.equals(to.getTag()) && cseq != null) {
            if (cseq.getSeqNumber() <= call.remoteCseq) status = 500;
            else if (call.accepted) { call.remoteCseq = cseq.getSeqNumber(); status = 200; }
        }
        tx.sendResponse(messages.createResponse(status, request));
        if (status == 200) finish(call, null);
        return true;
    }

    boolean incomingMessage(Request request, ServerTransaction tx, Peer peer) throws Exception {
        CallIdHeader id = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        Call call = id == null ? null : calls.get(id.getCallId());
        FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
        ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
        CSeqHeader cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
        // A channel can be the SIP identity; require its established dialog and actual peer before XML parsing.
        if (call == null || call.range == null || !call.established || call.completion.isDone()
                || !call.peer.equals(peer) || from == null || to == null
                || !call.remoteTag.equals(from.getTag()) || !call.localTag.equals(to.getTag())
                || cseq == null || !Request.MESSAGE.equals(cseq.getMethod())) {
            tx.sendResponse(messages.createResponse(481, request));
            return true;
        }
        ContentTypeHeader type = (ContentTypeHeader) request.getHeader(ContentTypeHeader.NAME);
        if (type == null || !"application".equalsIgnoreCase(type.getContentType())
                || !"MANSCDP+xml".equalsIgnoreCase(type.getContentSubType())) {
            tx.sendResponse(messages.createResponse(415, request));
            return true;
        }
        GbXml.Message content = GbXml.parse(request.getRawContent(), options.maxMessageBytes(),
                options.maxCatalogItems(), options.maxRecordItems());
        int status;
        if (!"MediaStatus".equals(content.command()) || !"Notify".equals(content.kind())) status = 400;
        else if (!call.channel.equals(content.deviceId())) status = 481;
        else if (cseq.getSeqNumber() <= call.remoteCseq) status = 500;
        else if ("121".equals(content.status())) { call.remoteCseq = cseq.getSeqNumber(); status = 200; }
        else status = 400;
        tx.sendResponse(messages.createResponse(status, request));
        if (status == 200) {
            try { bye(call); }
            catch (Exception e) { finish(call, new IllegalStateException("Cannot stop completed playback", e)); }
        }
        return true;
    }

    private CompletableFuture<Void> control(Call call, GbPlaybackControl command) {
        synchronized (lock) {
            Objects.requireNonNull(command, "Playback control");
            if (closed || call.range == null || call.downloadRequest != null || !call.established
                    || call.aborted || call.stopping || call.completion.isDone())
                throw new IllegalStateException("Controls require an established historical playback session");
            if (command.type() == GbPlaybackControl.Type.SEEK && command.position().getSeconds()
                    >= call.range.endTime().getEpochSecond() - call.range.startTime().getEpochSecond())
                throw new IllegalArgumentException("Seek must be within the playback interval");
            // Caller continuations run before our registered cleanup in CompletableFuture's LIFO completion stack.
            if (call.control != null && call.control.result.isDone()) clearControl(call, null);
            if (call.control != null) throw new IllegalStateException("Playback control already pending");
            PendingControl pending = new PendingControl(++call.sipCseq, ++call.rtspCseq);
            call.control = pending;
            // Completion can originate on a caller thread (cancel, complete, orTimeout).
            pending.result.whenComplete((unused, error) -> {
                synchronized (lock) { if (call.control == pending) clearControl(call, null); }
            });
            try {
                Request request = request(call, Request.INFO, pending.sipCseq, call.remoteTag);
                request.setContent(GbMansRtsp.request(command, pending.rtspCseq),
                        headers.createContentTypeHeader("Application", "MANSRTSP"));
                pending.transaction = providers.get(call.peer.transport).getNewClientTransaction(request);
                pending.transaction.setApplicationData(call.id);
                expect(call, request);
                pending.transaction.sendRequest();
            } catch (Exception e) { clearControl(call, new IllegalStateException("Cannot send playback control", e)); }
            return pending.result;
        }
    }

    private void controlResponse(Call call, Response response, CSeqHeader cseq, ViaHeader via, ToHeader to) {
        PendingControl pending = call.control;
        if (pending == null || pending.transaction == null || cseq.getSeqNumber() != pending.sipCseq
                || !Objects.equals(call.remoteTag, to.getTag())
                || !Objects.equals(((ViaHeader) pending.transaction.getRequest().getHeader(ViaHeader.NAME)).getBranch(), via.getBranch())) return;
        if (pending.result.isDone()) { clearControl(call, null); return; }
        if (System.nanoTime() >= pending.deadline) { clearControl(call, new TimeoutException("Playback control timed out")); return; }
        int status = response.getStatusCode();
        if (status < 200) return;
        if (status == 481) { abort(call, new IllegalStateException("Playback dialog no longer exists")); return; }
        if (status >= 300) { clearControl(call, new IllegalStateException("Playback control rejected: " + status)); return; }
        try {
            byte[] body = response.getRawContent();
            if (body != null && body.length > 0) {
                ContentTypeHeader type = (ContentTypeHeader) response.getHeader(ContentTypeHeader.NAME);
                if (type == null || !"application".equalsIgnoreCase(type.getContentType())
                        || !"MANSRTSP".equalsIgnoreCase(type.getContentSubType()))
                    throw new IllegalArgumentException("Invalid playback control response content type");
                GbMansRtsp.validateResponse(body, pending.rtspCseq, options.maxMessageBytes());
            }
            clearControl(call, null);
        } catch (IllegalArgumentException e) { clearControl(call, e); }
    }

    private void clearControl(Call call, Exception failure) {
        PendingControl pending = call.control;
        if (pending == null) return;
        call.control = null;
        if (pending.transaction != null) {
            responseGuard.forget(pending.transaction.getRequest());
            terminate(pending.transaction);
        }
        if (failure == null) pending.result.complete(null);
        else pending.result.completeExceptionally(failure);
    }

    private CompletableFuture<Void> stop(Call call) {
        synchronized (lock) {
            if (call.established && !call.completion.isDone()) {
                try { bye(call); }
                catch (Exception e) { finish(call, new IllegalStateException("Cannot send playback BYE", e)); }
            }
            return call.completion.copy();
        }
    }
    private void bye(Call call) throws Exception {
        if (call.bye != null || call.stopping || call.remoteTag == null) return;
        call.stopping = true;
        clearControl(call, new IllegalStateException("Playback is stopping"));
        call.byeCseq = ++call.sipCseq;
        Request request = request(call, Request.BYE, call.byeCseq, call.remoteTag);
        call.bye = providers.get(call.peer.transport).getNewClientTransaction(request);
        call.bye.setApplicationData(call.id);
        expect(call, request);
        call.stopDeadline = System.nanoTime() + options.stopTimeout().toNanos();
        call.bye.sendRequest();
    }
    private void cancel(Call call) throws Exception {
        if (!call.provisional || call.inviteFinal || call.cancel != null || call.invite == null) return;
        call.cancel = providers.get(call.peer.transport).getNewClientTransaction(call.invite.createCancel());
        call.cancel.setApplicationData(call.id);
        expect(call, call.cancel.getRequest());
        call.cancel.sendRequest();
    }
    private void abort(Call call, Exception failure) {
        call.aborted = true;
        try { if (call.accepted) bye(call); else cancel(call); }
        catch (Exception cleanup) { failure.addSuppressed(cleanup); }
        finish(call, failure);
    }
    private void finish(Call call, Exception failure) {
        // Keep the dialog/SSRC bounded but reachable for duplicate or late 2xx during 64*T1.
        if (call.cleanupAt == Long.MAX_VALUE) call.cleanupAt = System.nanoTime() + CLEANUP_NANOS;
        call.established = false;
        clearControl(call, failure == null ? new IllegalStateException("Playback ended") : failure);
        if (!call.result.isDone()) call.result.completeExceptionally(failure == null
                ? new IllegalStateException("Playback ended before establishment") : failure);
        if (failure == null) call.completion.complete(null);
        else call.completion.completeExceptionally(failure);
    }
    void failDevice(String device, String reason) {
        for (Call call : List.copyOf(calls.values())) {
            if (call.device.equals(device) && !call.completion.isDone()) abort(call, new IllegalStateException(reason));
        }
    }
    void maintain() {
        long now = System.nanoTime();
        for (Call call : List.copyOf(calls.values())) {
            if (call.control != null && now >= call.control.deadline)
                clearControl(call, new TimeoutException("Playback control timed out"));
            if (now >= call.cleanupAt) {
                calls.remove(call.id);
                responseGuard.forgetCall(call.id);
                terminate(call.invite); terminate(call.cancel); terminate(call.bye);
            } else if (abandoned(call) && !call.aborted) abort(call, new CancellationException("Playback result abandoned"));
            else if (!call.accepted && !call.completion.isDone() && now >= call.inviteDeadline)
                abort(call, new TimeoutException("Playback INVITE timed out"));
            else if (call.bye != null && now >= call.stopDeadline) {
                call.stopDeadline = Long.MAX_VALUE;
                terminate(call.bye);
                if (!call.completion.isDone()) finish(call, new TimeoutException("Playback BYE timed out"));
            }
        }
    }
    void timeout(TimeoutEvent event) {
        if (!event.isServerTransaction() && event.getClientTransaction() != null
                && event.getClientTransaction().getApplicationData() instanceof String id) {
            Call call = calls.get(id);
            if (call != null && !call.completion.isDone()) {
                if (Request.INFO.equals(event.getClientTransaction().getRequest().getMethod())) {
                    if (call.control != null && call.control.transaction == event.getClientTransaction())
                        clearControl(call, new TimeoutException("Playback control SIP transaction timed out"));
                } else abort(call, new TimeoutException("Playback SIP transaction timed out"));
            }
        }
    }
    void close() {
        closed = true;
        for (Call call : List.copyOf(calls.values())) {
            if (!call.completion.isDone()) abort(call, new IllegalStateException("GB28181 server closed"));
            terminate(call.invite); terminate(call.cancel); terminate(call.bye);
            responseGuard.forgetCall(call.id);
        }
        calls.clear();
    }
    private static void terminate(ClientTransaction tx) {
        if (tx != null && tx.getState() != TransactionState.TERMINATED)
            try { tx.terminate(); } catch (ObjectInUseException ignored) { }
    }
    private static boolean abandoned(Call call) {
        return call.result.isDone() && !call.established && !call.completion.isDone();
    }
    private void expect(Call call, Request request) {
        responseGuard.expect(request, call.peer.host, call.peer.port, call.peer.transport);
    }

    private Request request(Call call, String method, long cseq, String remoteTag) throws Exception {
        SipURI uri = addresses.createSipURI(call.channel, call.peer.host);
        uri.setPort(call.peer.port); uri.setTransportParam(call.peer.transport.toLowerCase(Locale.ROOT));
        var from = headers.createFromHeader(addresses.createAddress("sip:" + options.serverId() + "@" + options.realm()), call.localTag);
        var to = headers.createToHeader(addresses.createAddress("sip:" + call.channel + "@" + options.realm()), remoteTag);
        ViaHeader via = headers.createViaHeader(options.advertisedAddress(), options.port(), call.peer.transport,
                "z9hG4bK" + UUID.randomUUID().toString().replace("-", ""));
        via.setRPort();
        Request request = messages.createRequest(uri, method, headers.createCallIdHeader(call.id),
                headers.createCSeqHeader(cseq, method), from, to, new ArrayList<>(List.of(via)), headers.createMaxForwardsHeader(70));
        request.addHeader(headers.createHeader("X-GB-Ver", "3.0"));
        if (Request.INVITE.equals(method)) {
            SipURI contact = addresses.createSipURI(options.serverId(), options.advertisedAddress());
            contact.setPort(options.port()); contact.setTransportParam(call.peer.transport.toLowerCase(Locale.ROOT));
            request.addHeader(headers.createContactHeader(addresses.createAddress(contact)));
        }
        return request;
    }
    private String ssrc(boolean historical) {
        for (int attempt = 0; attempt < 10000; attempt++) {
            nextSsrc = (nextSsrc + 1) % 10000;
            String candidate = (historical ? "1" : "0") + options.serverId().substring(3, 8) + String.format(Locale.ROOT, "%04d", nextSsrc);
            if (calls.values().stream().noneMatch(c -> c.ssrc.equals(candidate))) return candidate;
        }
        throw new IllegalStateException("Playback SSRC capacity exceeded");
    }
    record Peer(String host, int port, String transport) {
        boolean matches(String host, int port) {
            if (host == null || this.port != port) return false;
            try { return InetAddress.getByName(this.host).equals(InetAddress.getByName(host)); }
            catch (java.net.UnknownHostException e) { return false; }
        }
    }
    private final class PendingControl {
        final long sipCseq, rtspCseq;
        final long deadline = System.nanoTime() + options.queryTimeout().toNanos();
        final CompletableFuture<Void> result = new CompletableFuture<>();
        ClientTransaction transaction;
        PendingControl(long sipCseq, long rtspCseq) { this.sipCseq = sipCseq; this.rtspCseq = rtspCseq; }
    }
    private final class Call {
        final String device, channel, id, ssrc;
        final String localTag = UUID.randomUUID().toString().replace("-", "");
        final GbRtpTarget target;
        final GbPlaybackRange range;
        final GbDownloadRequest downloadRequest;
        OptionalLong downloadFileSize = OptionalLong.empty();
        final Peer peer;
        final long inviteDeadline = System.nanoTime() + options.inviteTimeout().toNanos();
        final CompletableFuture<GbPlaySession> result = new CompletableFuture<>();
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        ClientTransaction invite, cancel, bye;
        PendingControl control;
        long sipCseq = 1, rtspCseq, byeCseq;
        String inviteBranch, remoteTag;
        boolean provisional, inviteFinal, accepted, established, aborted, stopping;
        long remoteCseq = -1;
        long stopDeadline = Long.MAX_VALUE;
        long cleanupAt = Long.MAX_VALUE;
        Call(String device, String channel, GbRtpTarget target, GbPlaybackRange range,
             GbDownloadRequest downloadRequest, Peer peer, String id, String ssrc) {
            this.device = device; this.channel = channel; this.target = target;
            this.peer = peer; this.id = id; this.ssrc = ssrc; this.range = range;
            this.downloadRequest = downloadRequest;
        }
    }
}

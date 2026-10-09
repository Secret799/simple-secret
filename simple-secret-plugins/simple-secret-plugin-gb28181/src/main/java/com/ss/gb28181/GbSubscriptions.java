package com.ss.gb28181;

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
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Direct registered-device subscriptions; all state is protected by the owning server's monitor. */
final class GbSubscriptions<R, P, H> {
    private static final long CLEANUP_NANOS = TimeUnit.SECONDS.toNanos(32);
    private final Object lock;
    private final GbServerOptions options;
    private final Map<String, SipProvider> providers;
    private final ResponseSourceGuard guard;
    private final Admission<P> admission;
    private final Type<R, P, H> type;
    private final int capacity;
    private int nextEventId;
    private final HeaderFactoryImpl headers = new HeaderFactoryImpl();
    private final AddressFactoryImpl addresses = new AddressFactoryImpl();
    private final MessageFactoryImpl messages = new MessageFactoryImpl();
    private final Map<String, Subscription> subscriptions = new HashMap<>();
    private int nextSn;
    private boolean closed;

    GbSubscriptions(Object lock, GbServerOptions options, Map<String, SipProvider> providers,
                    ResponseSourceGuard guard, int capacity, Type<R, P, H> type, Admission<P> admission) {
        this.lock = lock; this.options = options; this.providers = providers; this.guard = guard;
        this.admission = admission; this.capacity = capacity; this.type = type;
    }

    CompletableFuture<H> subscribe(String device, String target, R request, GbPlayback.Peer peer) {
        maintain();
        if (closed) throw new IllegalStateException(type.command() + " subscription server is closed");
        if (subscriptions.size() >= capacity)
            throw new IllegalStateException(type.command() + " subscription capacity exceeded");
        String id = providers.get(peer.transport()).getNewCallId().getCallId();
        Subscription sub = new Subscription(device, target, id, request, peer);
        subscriptions.put(id, sub);
        sub.opening.whenComplete((value, failure) -> {
            synchronized (lock) {
                if (!sub.published && !sub.retired) fail(sub, new CancellationException(type.command() + " subscription result abandoned"));
            }
        });
        try { sub.initial = send(sub, Kind.INITIAL); }
        catch (Exception failure) { fail(sub, new IllegalStateException("Cannot send " + type.command() + " SUBSCRIBE", failure)); }
        return sub.opening;
    }

    boolean response(ResponseEvent event) {
        Response response = event.getResponse();
        CallIdHeader id = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        Subscription sub = id == null ? null : subscriptions.get(id.getCallId());
        if (sub == null) return false;
        if (!(event instanceof ResponseEventExt ext) || !sub.peer.matches(ext.getRemoteIpAddress(), ext.getRemotePort())
                || !sub.peer.transport().equalsIgnoreCase(((SipProvider) event.getSource()).getListeningPoint().getTransport())) return true;
        FromHeader from = (FromHeader) response.getHeader(FromHeader.NAME);
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
        ViaHeader via = (ViaHeader) response.getHeader(ViaHeader.NAME);
        CSeqHeader seq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        if (from == null || to == null || via == null || seq == null || !sub.localTag.equals(from.getTag())
                || !Request.SUBSCRIBE.equals(seq.getMethod())) return true;
        Pending pending = match(sub, seq.getSeqNumber(), via.getBranch());
        if (pending == null || sub.remoteTag != null && !sub.remoteTag.equals(to.getTag())) return true;
        int status = response.getStatusCode();
        if (status < 200) return true;
        if (!sub.retired && System.nanoTime() >= pending.deadline)
            fail(sub, new TimeoutException(type.command() + " SUBSCRIBE response timed out"));
        if (!sub.retired && !sub.stopping && expired(sub, System.nanoTime()))
            fail(sub, new TimeoutException(type.command() + " subscription expired"));
        // Initial expectations survive abandoned opening futures until a final response or cleanup deadline.
        if (pending.kind != Kind.INITIAL && pending != sub.renewal && pending != sub.unsubscribe) return true;
        retire(sub, pending);
        if (status >= 300) {
            if (!sub.retired) fail(sub, new IllegalStateException(type.command() + " SUBSCRIBE rejected: " + status));
            return true;
        }
        if (!validTag(to.getTag())) {
            if (!sub.retired) fail(sub, new IllegalArgumentException("Missing or oversized " + type.command() + " dialog tag"));
            return true;
        }
        sub.remoteTag = to.getTag();
        try {
            if (response.getRawContent() != null && response.getRawContent().length > options.maxMessageBytes())
                throw new IllegalArgumentException("Oversized " + type.command() + " SUBSCRIBE response");
            if (response.getHeader(ContactHeader.NAME) == null || response.getHeader(RecordRouteHeader.NAME) != null)
                throw new IllegalArgumentException(type.command() + " subscription requires a direct-device Contact without Record-Route");
            ExpiresHeader expires = (ExpiresHeader) response.getHeader(ExpiresHeader.NAME);
            if (!single(response, ExpiresHeader.NAME)) throw new IllegalArgumentException("Duplicate Expires");
            if (pending.kind == Kind.STOP) {
                if (expires != null && expires.getExpires() != 0) throw new IllegalArgumentException("Invalid unsubscribe Expires");
                if (!sub.retired) finish(sub, null);
                return true;
            }
            if (expires == null || expires.getExpires() < 1 || expires.getExpires() > type.expires(sub.request))
                throw new IllegalArgumentException("Invalid granted " + type.command() + " subscription lifetime");
            if (sub.retired || sub.stopping || closed) { bestEffortStop(sub); return true; }
            long now = System.nanoTime();
            long granted = TimeUnit.SECONDS.toNanos(expires.getExpires());
            sub.expiresAt = pending.kind == Kind.INITIAL ? Math.min(sub.expiresAt, now + granted) : now + granted;
            sub.renewAt = now + (sub.expiresAt - now) * 4 / 5;
            if (pending.kind == Kind.INITIAL) {
                sub.initialAccepted = true;
                sub.initialNotifyDeadline = now + CLEANUP_NANOS;
                var session = type.handle(sub.device, sub.target, sub.id, sub.request,
                        sub.completion, () -> stop(sub));
                sub.published = true;
                if (!sub.opening.complete(session)) {
                    sub.published = false;
                    fail(sub, new CancellationException(type.command() + " subscription result abandoned"));
                }
            }
        } catch (Exception failure) {
            if (!sub.retired) fail(sub, failure);
            else bestEffortStop(sub);
        }
        return true;
    }

    boolean owns(Request request) {
        CallIdHeader id = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        return id != null && id.getCallId().length() <= 256 && subscriptions.containsKey(id.getCallId());
    }

    void notify(Request request, ServerTransaction transaction, GbPlayback.Peer peer) throws Exception {
        maintain();
        CallIdHeader id = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        Subscription sub = id == null || id.getCallId().length() > 256 ? null : subscriptions.get(id.getCallId());
        FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);
        ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);
        if (sub == null || !sub.peer.equals(peer) || from == null || to == null || !sub.localTag.equals(to.getTag())
                || !validTag(from.getTag()) || sub.remoteTag != null && !sub.remoteTag.equals(from.getTag())
                || !(from.getAddress().getURI() instanceof SipURI identity)
                || !sub.device.equals(identity.getUser()) && !sub.target.equals(identity.getUser())) {
            reply(transaction, request, 481); return;
        }
        EventHeader event = (EventHeader) request.getHeader(EventHeader.NAME);
        if (event == null || !single(request, EventHeader.NAME) || event.toString().length() > 256
                || !type.eventType().equalsIgnoreCase(event.getEventType()) || !Objects.equals(sub.eventId, event.getEventId())) {
            reply(transaction, request, 489); return;
        }
        SubscriptionStateHeader state = (SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME);
        CSeqHeader seq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
        if (state == null || !single(request, SubscriptionStateHeader.NAME) || state.toString().length() > 1024
                || seq == null || seq.getSeqNumber() <= 0 || !Request.NOTIFY.equals(seq.getMethod())) {
            reply(transaction, request, 400); return;
        }
        String value = state.getState().toLowerCase(Locale.ROOT);
        boolean terminal = "terminated".equals(value);
        if (!terminal && !"active".equals(value) && !"pending".equals(value)) { reply(transaction, request, 400); return; }
        // RI stores standard parameters in dedicated fields, outside Parameters.getParameter().
        long expires = state.getExpires();
        if (!terminal && expires != -1 && (expires < 1 || expires > type.expires(sub.request))) {
            reply(transaction, request, 400); return;
        }
        if (seq.getSeqNumber() <= sub.remoteCseq) { reply(transaction, request, 500); return; }
        byte[] body = request.getRawContent();
        P payload = null;
        if (body != null && body.length > 0) {
            ContentTypeHeader mime = (ContentTypeHeader) request.getHeader(ContentTypeHeader.NAME);
            if (mime == null || !"application".equalsIgnoreCase(mime.getContentType())
                    || !"MANSCDP+xml".equalsIgnoreCase(mime.getContentSubType())) { reply(transaction, request, 415); return; }
            try {
                GbXml.Message content = GbXml.parse(body, options.maxMessageBytes(), options.maxCatalogItems(),
                        options.maxRecordItems(), options.maxMobilePositionItems());
                payload = type.decode(content, sub.device, sub.target);
            } catch (IllegalArgumentException malformed) { reply(transaction, request, 400); return; }
        }
        final long notificationExpires = expires;
        Runnable accepted = () -> {
            if (sub.remoteTag == null) {
                sub.remoteTag = from.getTag();
                if (sub.initial != null) guard.bindRemoteTag(sub.initial.transaction.getRequest(), sub.remoteTag);
            }
            sub.remoteCseq = seq.getSeqNumber();
            sub.initialNotified = true;
            if (!terminal && !sub.stopping && !sub.retired && notificationExpires > 0) {
                long now = System.nanoTime();
                sub.expiresAt = Math.min(sub.expiresAt, now + TimeUnit.SECONDS.toNanos(notificationExpires));
                sub.renewAt = Math.min(sub.renewAt, now + (sub.expiresAt - now) * 4 / 5);
            }
            if (terminal) {
                sub.remoteTerminated = true;
                if (!sub.retired) finish(sub, null);
            }
            else if (sub.stopping || sub.retired) bestEffortStop(sub);
        };
        if (payload != null && !sub.stopping && !sub.retired) {
            admission.accept(transaction, request, sub.device, sub.id, payload, accepted, () -> !sub.suppressDelivery);
        } else {
            reply(transaction, request, 200);
            accepted.run();
        }
    }

    private CompletableFuture<Void> stop(Subscription sub) {
        synchronized (lock) {
            if (!sub.retired && !sub.stopping) {
                sub.stopping = true; sub.suppressDelivery = true;
                retire(sub, sub.renewal);
                try { sub.unsubscribe = send(sub, Kind.STOP); }
                catch (Exception failure) { fail(sub, new IllegalStateException("Cannot unsubscribe " + type.command() + "", failure)); }
            }
            return sub.completion.copy();
        }
    }
    private void fail(Subscription sub, Exception failure) {
        if (sub.retired) return;
        sub.stopping = true; sub.suppressDelivery = true;
        retire(sub, sub.renewal);
        bestEffortStop(sub);
        finish(sub, failure);
    }
    private void finish(Subscription sub, Exception failure) {
        if (sub.retired) return;
        sub.retired = true;
        sub.cleanupAt = System.nanoTime() + CLEANUP_NANOS;
        retire(sub, sub.renewal);
        if (failure == null) retire(sub, sub.unsubscribe);
        // Publish terminal state before completing futures: continuations may reenter stop or close.
        if (!sub.opening.isDone()) sub.opening.completeExceptionally(failure == null
                ? new IllegalStateException(type.command() + " subscription ended before acceptance") : failure);
        if (failure == null) sub.completion.complete(null); else sub.completion.completeExceptionally(failure);
    }
    private void bestEffortStop(Subscription sub) {
        if (sub.remoteTag == null || sub.stopSent || sub.remoteTerminated) return;
        try { sub.unsubscribe = send(sub, Kind.STOP); }
        catch (Exception ignored) { /* The local failure and bounded cleanup remain authoritative. */ }
    }

    void maintain() {
        long now = System.nanoTime();
        for (Subscription sub : List.copyOf(subscriptions.values())) {
            if (closed) return;
            if (now >= sub.cleanupAt) {
                subscriptions.remove(sub.id); retire(sub, sub.initial); retire(sub, sub.renewal); retire(sub, sub.unsubscribe);
            } else if (!sub.retired) {
                if (!sub.published && sub.opening.isDone()) fail(sub, new CancellationException(type.command() + " subscription result abandoned"));
                else if (sub.stopping) {
                    if (sub.unsubscribe != null && now >= sub.unsubscribe.deadline)
                        fail(sub, new TimeoutException(type.command() + " unsubscribe timed out"));
                } else if (expired(sub, now)) fail(sub, new TimeoutException(type.command() + " subscription expired"));
                else if (sub.initial != null && now >= sub.initial.deadline)
                    fail(sub, new TimeoutException(type.command() + " SUBSCRIBE timed out"));
                else if (sub.initialAccepted && !sub.initialNotified && now >= sub.initialNotifyDeadline)
                    fail(sub, new TimeoutException("Initial " + type.command() + " NOTIFY timed out"));
                else if (sub.renewal != null && now >= sub.renewal.deadline)
                    fail(sub, new TimeoutException(type.command() + " renewal timed out"));
                else if (sub.initialAccepted && sub.renewal == null && now >= sub.renewAt) {
                    try { sub.renewal = send(sub, Kind.RENEW); }
                    catch (Exception failure) { fail(sub, new IllegalStateException("Cannot renew " + type.command() + " subscription", failure)); }
                }
            }
            if (sub.retired && sub.unsubscribe != null && now >= sub.unsubscribe.deadline) retire(sub, sub.unsubscribe);
        }
    }
    void timeout(TimeoutEvent event) {
        if (event.isServerTransaction() || event.getClientTransaction() == null
                || !(event.getClientTransaction().getApplicationData() instanceof Pending pending) || pending.owner != this) return;
        Subscription sub = subscriptions.get(pending.id);
        if (sub == null || pending != sub.initial && pending != sub.renewal && pending != sub.unsubscribe) return;
        if (!sub.retired) fail(sub, new TimeoutException(type.command() + " SUBSCRIBE SIP transaction timed out"));
        if (pending.kind != Kind.INITIAL) retire(sub, pending);
    }
    void failDevice(String device, String reason) {
        for (Subscription sub : List.copyOf(subscriptions.values()))
            if (sub.device.equals(device) && !sub.retired) fail(sub, new IllegalStateException(reason));
    }
    void close() {
        closed = true;
        for (Subscription sub : List.copyOf(subscriptions.values())) {
            sub.suppressDelivery = true;
            if (!sub.retired) fail(sub, new IllegalStateException("GB28181 server closed"));
            retire(sub, sub.initial); retire(sub, sub.renewal); retire(sub, sub.unsubscribe);
        }
        subscriptions.clear();
    }
    private boolean expired(Subscription sub, long now) { return now >= sub.expiresAt; }
    private Pending send(Subscription sub, Kind kind) throws Exception {
        if (kind == Kind.STOP) sub.stopSent = true;
        SipURI uri = addresses.createSipURI(sub.target, sub.peer.host());
        uri.setPort(sub.peer.port()); uri.setTransportParam(sub.peer.transport().toLowerCase(Locale.ROOT));
        var from = headers.createFromHeader(addresses.createAddress("sip:" + options.serverId() + "@" + options.realm()), sub.localTag);
        var to = headers.createToHeader(addresses.createAddress(uri), sub.remoteTag);
        ViaHeader via = headers.createViaHeader(options.advertisedAddress(), options.port(), sub.peer.transport(),
                "z9hG4bK" + UUID.randomUUID().toString().replace("-", ""));
        via.setRPort();
        Request outgoing = messages.createRequest(uri, Request.SUBSCRIBE, headers.createCallIdHeader(sub.id),
                headers.createCSeqHeader(++sub.localCseq, Request.SUBSCRIBE), from, to,
                new ArrayList<>(List.of(via)), headers.createMaxForwardsHeader(70));
        SipURI contact = addresses.createSipURI(options.serverId(), options.advertisedAddress());
        contact.setPort(options.port()); contact.setTransportParam(sub.peer.transport().toLowerCase(Locale.ROOT));
        outgoing.addHeader(headers.createContactHeader(addresses.createAddress(contact)));
        EventHeader event = headers.createEventHeader(type.eventType());
        if (sub.eventId != null) event.setEventId(sub.eventId);
        outgoing.addHeader(event);
        outgoing.addHeader(headers.createExpiresHeader(kind == Kind.STOP ? 0 : (int) type.expires(sub.request)));
        outgoing.addHeader(headers.createHeader("X-GB-Ver", "3.0"));
        nextSn = nextSn == Integer.MAX_VALUE ? 1 : nextSn + 1;
        outgoing.setContent(type.xml(sub.target, nextSn, sub.request),
                headers.createContentTypeHeader("Application", "MANSCDP+xml"));
        ClientTransaction transaction = providers.get(sub.peer.transport()).getNewClientTransaction(outgoing);
        Pending pending = new Pending(this, sub.id, kind, sub.localCseq, transaction,
                System.nanoTime() + (kind == Kind.STOP ? options.stopTimeout() : options.queryTimeout()).toNanos());
        transaction.setApplicationData(pending);
        try {
            guard.expect(outgoing, sub.peer.host(), sub.peer.port(), sub.peer.transport());
            transaction.sendRequest();
        } catch (Exception failure) {
            guard.forget(outgoing); terminate(transaction); throw failure;
        }
        return pending;
    }
    private Pending match(Subscription sub, long cseq, String branch) {
        for (Pending pending : new Pending[]{sub.initial, sub.renewal, sub.unsubscribe})
            if (pending != null && pending.cseq == cseq
                    && Objects.equals(((ViaHeader) pending.transaction.getRequest().getHeader(ViaHeader.NAME)).getBranch(), branch))
                return pending;
        return null;
    }
    private void retire(Subscription sub, Pending pending) {
        if (pending == null) return;
        if (sub.initial == pending) sub.initial = null;
        if (sub.renewal == pending) sub.renewal = null;
        if (sub.unsubscribe == pending) sub.unsubscribe = null;
        guard.forget(pending.transaction.getRequest()); terminate(pending.transaction);
    }
    private static void terminate(ClientTransaction transaction) {
        if (transaction.getState() != TransactionState.TERMINATED)
            try { transaction.terminate(); } catch (ObjectInUseException ignored) { }
    }
    private void reply(ServerTransaction transaction, Request request, int status) throws Exception {
        transaction.sendResponse(messages.createResponse(status, request));
    }
    private static boolean validTag(String tag) { return tag != null && tag.matches("[a-zA-Z0-9.!%*_+`'~-]{1,128}"); }
    private static boolean single(javax.sip.message.Message message, String name) {
        var values = message.getHeaders(name);
        if (!values.hasNext()) return true;
        values.next(); return !values.hasNext();
    }
    interface Type<R, P, H> {
        String command();
        String eventType();
        boolean usesEventId();
        long expires(R request);
        byte[] xml(String target, int sn, R request);
        P decode(GbXml.Message content, String device, String target);
        H handle(String device, String target, String id, R request, CompletableFuture<Void> completion,
                 java.util.function.Supplier<CompletableFuture<Void>> stop);
    }
    @FunctionalInterface interface Admission<P> {
        void accept(ServerTransaction transaction, Request request, String device, String callId, P payload,
                    Runnable accepted, BooleanSupplier eligible) throws Exception;
    }
    private enum Kind { INITIAL, RENEW, STOP }
    private record Pending(Object owner, String id, Kind kind, long cseq, ClientTransaction transaction, long deadline) { }
    private final class Subscription {
        final String device, target, id;
        final String eventId;
        final String localTag = UUID.randomUUID().toString().replace("-", "");
        final R request;
        final GbPlayback.Peer peer;
        final CompletableFuture<H> opening = new CompletableFuture<>();
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        Pending initial, renewal, unsubscribe;
        String remoteTag;
        long localCseq, remoteCseq;
        long expiresAt = Long.MAX_VALUE, renewAt = Long.MAX_VALUE, cleanupAt = Long.MAX_VALUE;
        long initialNotifyDeadline = Long.MAX_VALUE;
        boolean published, initialAccepted, initialNotified, stopping, retired, stopSent, remoteTerminated, suppressDelivery;
        Subscription(String device, String target, String id, R request, GbPlayback.Peer peer) {
            this.device = device; this.target = target; this.id = id; this.request = request; this.peer = peer;
            nextEventId = nextEventId == Integer.MAX_VALUE ? 1 : nextEventId + 1;
            this.eventId = type.usesEventId() ? Integer.toString(nextEventId) : null;
        }
    }
}

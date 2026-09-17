package com.ss.gb28181.internal;

import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.SIPMessageValve;

import javax.sip.SipStack;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Rejects unrelated responses before the RI can advance a client transaction's state. */
public final class ResponseSourceGuard implements SIPMessageValve {
    private final ConcurrentHashMap<Key, Expected> expected = new ConcurrentHashMap<>();
    private final int maxEntries;
    private volatile boolean destroyed;

    public ResponseSourceGuard(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("response expectation capacity must be positive");
        this.maxEntries = maxEntries;
    }

    /** Register after the RI assigns the transaction branch and before sending the request. */
    public synchronized void expect(Request request, String host, int port, String transport) {
        checkOpen();
        if (request == null || port < 1 || port > 65535)
            throw new IllegalArgumentException("invalid response expectation");
        String protocol = transport == null ? "" : transport.toUpperCase(Locale.ROOT);
        if (!"UDP".equals(protocol) && !"TCP".equals(protocol))
            throw new IllegalArgumentException("unsupported response transport");
        var via = (ViaHeader) request.getHeader(ViaHeader.NAME);
        var cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
        var call = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        var from = (FromHeader) request.getHeader(FromHeader.NAME);
        var to = (ToHeader) request.getHeader(ToHeader.NAME);
        if (via == null || blank(via.getBranch()) || cseq == null || blank(cseq.getMethod())
                || !cseq.getMethod().equals(request.getMethod()) || call == null || blank(call.getCallId())
                || from == null || blank(from.getTag()) || to == null)
            throw new IllegalArgumentException("request is missing response correlation headers");
        var key = new Key(via.getBranch(), cseq.getMethod());
        var value = new Expected(call.getCallId(), cseq.getSeqNumber(), from.getTag(), to.getTag(),
                numericAddress(host), port, protocol);
        Expected previous = expected.get(key);
        if (previous != null) {
            if (!previous.equals(value)) throw new IllegalStateException("response transaction already registered");
            return;
        }
        if (expected.size() >= maxEntries) throw new IllegalStateException("response expectation capacity exhausted");
        expected.put(key, value);
    }

    /** Tightens an existing initial transaction after a validated early NOTIFY establishes its dialog tag. */
    public synchronized void bindRemoteTag(Request request, String remoteTag) {
        checkOpen();
        if (request == null || remoteTag == null || !remoteTag.matches("[a-zA-Z0-9.!%*_+`'~-]{1,128}"))
            throw new IllegalArgumentException("invalid remote dialog tag");
        var via = (ViaHeader) request.getHeader(ViaHeader.NAME);
        var cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
        var call = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        var from = (FromHeader) request.getHeader(FromHeader.NAME);
        if (via == null || cseq == null || call == null || from == null
                || !request.getMethod().equals(cseq.getMethod()))
            throw new IllegalArgumentException("missing response correlation headers");
        expected.compute(new Key(via.getBranch(), cseq.getMethod()), (key, value) -> {
            if (value == null || !value.callId().equals(call.getCallId()) || value.cseq() != cseq.getSeqNumber()
                    || !value.fromTag().equals(from.getTag())
                    || value.toTag() != null && !value.toTag().equals(remoteTag))
                throw new IllegalStateException("response expectation does not match dialog");
            return new Expected(value.callId(), value.cseq(), value.fromTag(), remoteTag,
                    value.address(), value.port(), value.transport());
        });
    }

    /** Removes one transaction while preserving INVITE expectations for late final responses. */
    public synchronized void forget(Request request) {
        if (request == null) return;
        var via = (ViaHeader) request.getHeader(ViaHeader.NAME);
        var cseq = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
        var call = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        if (via != null && cseq != null && call != null) {
            expected.computeIfPresent(new Key(via.getBranch(), cseq.getMethod()), (key, value) ->
                    value.callId().equals(call.getCallId()) && value.cseq() == cseq.getSeqNumber() ? null : value);
        }
    }

    public synchronized void forgetCall(String callId) {
        if (callId != null) expected.entrySet().removeIf(entry -> callId.equals(entry.getValue().callId()));
    }

    public synchronized void clear() { expected.clear(); }

    @Override public boolean processRequest(SIPRequest request, MessageChannel channel) { return true; }

    @Override
    public boolean processResponse(Response response, MessageChannel channel) {
        if (destroyed || response == null || channel == null) return false;
        var via = (ViaHeader) response.getHeader(ViaHeader.NAME);
        var cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        var call = (CallIdHeader) response.getHeader(CallIdHeader.NAME);
        var from = (FromHeader) response.getHeader(FromHeader.NAME);
        var to = (ToHeader) response.getHeader(ToHeader.NAME);
        if (via == null || cseq == null || call == null || from == null || to == null) return false;
        Expected value = expected.get(new Key(via.getBranch(), cseq.getMethod()));
        if (value == null || !value.callId().equals(call.getCallId()) || value.cseq() != cseq.getSeqNumber()
                || !value.fromTag().equals(from.getTag())
                || value.toTag() != null && !value.toTag().equals(to.getTag())
                || !value.transport().equalsIgnoreCase(channel.getTransport())) return false;

        // Via/received/rport describe routing, not authenticated packet provenance.
        InetAddress address = channel.getPeerPacketSourceAddress();
        int port = channel.getPeerPacketSourcePort();
        if ("TCP".equals(value.transport())) {
            // Some RI TCP channels expose only their connected socket endpoint.
            if (address == null) {
                try { address = numericAddress(channel.getPeerAddress()); }
                catch (IllegalArgumentException ignored) { return false; }
            }
            if (port < 1) port = channel.getPeerPort();
        }
        return value.address().equals(address) && value.port() == port && !destroyed;
    }

    @Override public synchronized void init(SipStack stack) { checkOpen(); }

    @Override public synchronized void destroy() {
        destroyed = true;
        expected.clear();
    }

    private void checkOpen() {
        if (destroyed) throw new IllegalStateException("response source guard is destroyed");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static InetAddress numericAddress(String host) {
        if (host == null || host.isEmpty() || host.length() > 80)
            throw new IllegalArgumentException("response source must be a numeric IP address");
        String value = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        try {
            // The colon and character check ensure getByName can only parse a literal, never resolve DNS.
            if (value.indexOf(':') >= 0 && value.matches("[0-9a-fA-F:.]+(%[0-9]+)?"))
                return InetAddress.getByName(value);
            String[] parts = value.split("\\.", -1);
            if (parts.length == 4) {
                byte[] bytes = new byte[4];
                for (int i = 0; i < parts.length; i++) {
                    if (!parts[i].matches("[0-9]{1,3}")) throw new IllegalArgumentException("invalid IPv4 source");
                    int octet = Integer.parseInt(parts[i]);
                    if (octet > 255) throw new IllegalArgumentException("invalid IPv4 source");
                    bytes[i] = (byte) octet;
                }
                return InetAddress.getByAddress(bytes);
            }
        } catch (UnknownHostException ignored) {
            throw new IllegalArgumentException("invalid response source address");
        }
        throw new IllegalArgumentException("response source must be a numeric IP address");
    }

    private record Key(String branch, String method) { }
    private record Expected(String callId, long cseq, String fromTag, String toTag,
                            InetAddress address, int port, String transport) { }
}

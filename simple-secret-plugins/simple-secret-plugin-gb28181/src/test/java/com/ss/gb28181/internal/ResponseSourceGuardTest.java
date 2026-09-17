package com.ss.gb28181.internal;

import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import org.junit.jupiter.api.Test;

import javax.sip.SipFactory;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseSourceGuardTest {
    private static final String HOST = "192.0.2.10";
    private static final int PORT = 5060;

    @Test
    void rejectsForgedFinalBeforeAcceptingTheRealResponseAndRetransmission() throws Exception {
        var guard = new ResponseSourceGuard(4);
        Request request = request("call-1", "INVITE", "z9hG4bK-1", null);
        guard.expect(request, HOST, PORT, "UDP");
        Response failure = response(request, 486);
        assertFalse(guard.processResponse(failure, channel("192.0.2.11", PORT, "UDP")));
        assertFalse(guard.processResponse(failure, channel(HOST, PORT + 1, "UDP")));
        assertFalse(guard.processResponse(failure, channel(HOST, PORT, "TCP")));
        Response success = response(request, 200);
        assertTrue(guard.processResponse(success, channel(HOST, PORT, "UDP")));
        assertTrue(guard.processResponse(success, channel(HOST, PORT, "UDP")));
    }

    @Test
    void rejectsEveryMismatchedTransactionAndDialogField() throws Exception {
        var guard = new ResponseSourceGuard(4);
        Request request = request("call-1", "BYE", "z9hG4bK-1", "remote-tag");
        guard.expect(request, HOST, PORT, "UDP");
        for (String field : new String[]{"call", "sequence", "method", "branch", "from", "to"}) {
            Response response = response(request, 200);
            switch (field) {
                case "call" -> ((CallIdHeader) response.getHeader(CallIdHeader.NAME)).setCallId("call-2");
                case "sequence" -> ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).setSeqNumber(2);
                case "method" -> ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).setMethod("INVITE");
                case "branch" -> ((ViaHeader) response.getHeader(ViaHeader.NAME)).setBranch("z9hG4bK-other");
                case "from" -> ((FromHeader) response.getHeader(FromHeader.NAME)).setTag("other-tag");
                case "to" -> ((ToHeader) response.getHeader(ToHeader.NAME)).setTag("other-tag");
            }
            assertFalse(guard.processResponse(response, channel(HOST, PORT, "UDP")), field);
        }
        assertTrue(guard.processResponse(response(request, 200), channel(HOST, PORT, "UDP")));
    }

    @Test
    void missingHeadersAndUnknownResponsesFailClosed() throws Exception {
        var guard = new ResponseSourceGuard(4);
        Request request = request("call-1", "MESSAGE", "z9hG4bK-1", null);
        assertFalse(guard.processResponse(response(request, 200), channel(HOST, PORT, "UDP")));
        guard.expect(request, HOST, PORT, "UDP");
        for (String header : new String[]{CallIdHeader.NAME, CSeqHeader.NAME, ViaHeader.NAME,
                FromHeader.NAME, ToHeader.NAME}) {
            Response response = response(request, 200);
            response.removeHeader(header);
            assertFalse(guard.processResponse(response, channel(HOST, PORT, "UDP")), header);
        }
        assertFalse(guard.processResponse(null, channel(HOST, PORT, "UDP")));
        assertFalse(guard.processResponse(response(request, 200), null));
        assertTrue(guard.processRequest((SIPRequest) request, channel(HOST, PORT, "UDP")));
    }

    @Test
    void packetSourceTakesPrecedenceOverPeerAndVia() throws Exception {
        var guard = new ResponseSourceGuard(4);
        Request request = request("call-1", "INVITE", "z9hG4bK-1", null);
        guard.expect(request, HOST, PORT, "UDP");
        Response response = response(request, 486);
        var via = (ViaHeader) response.getHeader(ViaHeader.NAME);
        via.setReceived(HOST);
        via.setParameter("rport", Integer.toString(PORT));
        assertFalse(guard.processResponse(response,
                new TestChannel(InetAddress.getByName("192.0.2.11"), PORT, HOST, PORT, "UDP")));
        assertTrue(guard.processResponse(response,
                new TestChannel(InetAddress.getByName(HOST), PORT, "192.0.2.11", PORT + 1, "UDP")));
        assertFalse(guard.processResponse(response, new TestChannel(null, -1, HOST, PORT, "UDP")));
    }

    @Test
    void tcpFallbackUsesNumericPeerAndSupportsIpv6() throws Exception {
        var guard = new ResponseSourceGuard(4);
        Request request = request("call-1", "INVITE", "z9hG4bK-1", null);
        guard.expect(request, "2001:db8::10", PORT, "tcp");
        Response response = response(request, 200);
        assertTrue(guard.processResponse(response,
                new TestChannel(null, -1, "2001:db8:0:0:0:0:0:10", PORT, "TCP")));
        assertFalse(guard.processResponse(response,
                new TestChannel(null, -1, "2001:db8::11", PORT, "TCP")));
        assertFalse(guard.processResponse(response,
                new TestChannel(null, -1, "localhost", PORT, "TCP")));
    }

    @Test
    void cancelCanShareInviteBranchAndCallCleanupPreservesOtherCalls() throws Exception {
        var guard = new ResponseSourceGuard(4);
        Request invite = request("call-1", "INVITE", "z9hG4bK-1", null);
        Request cancel = request("call-1", "CANCEL", "z9hG4bK-1", null);
        Request other = request("call-2", "MESSAGE", "z9hG4bK-2", null);
        for (Request request : new Request[]{invite, cancel, other}) guard.expect(request, HOST, PORT, "UDP");
        assertTrue(guard.processResponse(response(invite, 487), channel(HOST, PORT, "UDP")));
        assertTrue(guard.processResponse(response(cancel, 200), channel(HOST, PORT, "UDP")));
        guard.forgetCall("call-1");
        guard.forgetCall("call-1");
        assertFalse(guard.processResponse(response(invite, 200), channel(HOST, PORT, "UDP")));
        assertFalse(guard.processResponse(response(cancel, 200), channel(HOST, PORT, "UDP")));
        assertTrue(guard.processResponse(response(other, 200), channel(HOST, PORT, "UDP")));
        guard.clear();
        assertFalse(guard.processResponse(response(other, 200), channel(HOST, PORT, "UDP")));
    }

    @Test
    void boundsEntriesWithoutEvictingActiveTransactionsAndDestroyIsFinal() throws Exception {
        var guard = new ResponseSourceGuard(1);
        Request first = request("call-1", "MESSAGE", "z9hG4bK-1", null);
        Request second = request("call-2", "MESSAGE", "z9hG4bK-2", null);
        guard.init(null);
        guard.expect(first, HOST, PORT, "UDP");
        guard.expect(first, HOST, PORT, "UDP");
        assertThrows(IllegalStateException.class, () -> guard.expect(second, HOST, PORT, "UDP"));
        assertTrue(guard.processResponse(response(first, 200), channel(HOST, PORT, "UDP")));
        guard.forgetCall("call-1");
        assertDoesNotThrow(() -> guard.expect(second, HOST, PORT, "UDP"));
        guard.destroy();
        guard.destroy();
        assertFalse(guard.processResponse(response(second, 200), channel(HOST, PORT, "UDP")));
        assertThrows(IllegalStateException.class, () -> guard.expect(first, HOST, PORT, "UDP"));
        assertThrows(IllegalStateException.class, () -> guard.init(null));
    }

    @Test
    void validatesExpectationsAndPreventsAnExistingBranchBeingRebound() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ResponseSourceGuard(0));
        var guard = new ResponseSourceGuard(2);
        Request first = request("call-1", "MESSAGE", "z9hG4bK-1", null);
        for (String host : new String[]{null, "", "localhost", "example.invalid", "192.0.2.999"})
            assertThrows(IllegalArgumentException.class, () -> guard.expect(first, host, PORT, "UDP"));
        assertThrows(IllegalArgumentException.class, () -> guard.expect(first, HOST, 0, "UDP"));
        assertThrows(IllegalArgumentException.class, () -> guard.expect(first, HOST, 65536, "UDP"));
        assertThrows(IllegalArgumentException.class, () -> guard.expect(first, HOST, PORT, "TLS"));
        guard.expect(first, HOST, PORT, "UDP");
        Request second = request("call-2", "MESSAGE", "z9hG4bK-1", null);
        assertThrows(IllegalStateException.class, () -> guard.expect(second, HOST, PORT, "UDP"));
        assertTrue(guard.processResponse(response(first, 200), channel(HOST, PORT, "UDP")));
    }

    @Test
    void individualInfoCleanupReleasesCapacityWithoutLosingInviteGuard() throws Exception {
        var guard = new ResponseSourceGuard(2);
        Request invite = request("call-1", "INVITE", "z9hG4bK-invite", null);
        guard.expect(invite, HOST, PORT, "UDP");
        for (int i = 0; i < 20; i++) {
            Request info = request("call-1", "INFO", "z9hG4bK-info" + i, "remote-tag");
            guard.expect(info, HOST, PORT, "UDP");
            assertTrue(guard.processResponse(response(info, 200), channel(HOST, PORT, "UDP")));
            guard.forget(info);
            guard.forget(info);
            assertFalse(guard.processResponse(response(info, 200), channel(HOST, PORT, "UDP")));
            assertTrue(guard.processResponse(response(invite, 200), channel(HOST, PORT, "UDP")));
        }
    }

    @Test
    void earlyNotificationAtomicallyBindsTagWithoutChangingOtherResponseConstraints() throws Exception {
        var guard = new ResponseSourceGuard(2);
        Request initial = request("subscription", "SUBSCRIBE", "z9hG4bK-sub", null);
        Request other = request("other", "MESSAGE", "z9hG4bK-other", null);
        guard.expect(initial, HOST, PORT, "TCP"); guard.expect(other, HOST, PORT, "UDP");
        guard.bindRemoteTag(initial, "remote"); guard.bindRemoteTag(initial, "remote");
        Response valid = response(initial, 200);
        ((ToHeader) valid.getHeader(ToHeader.NAME)).setTag("remote");
        assertTrue(guard.processResponse(valid, channel(HOST, PORT, "TCP")));
        assertFalse(guard.processResponse(valid, channel(HOST, PORT + 1, "TCP")));
        assertFalse(guard.processResponse(valid, channel(HOST, PORT, "UDP")));
        assertFalse(guard.processResponse(response(initial, 200), channel(HOST, PORT, "TCP")));
        assertThrows(IllegalStateException.class, () -> guard.bindRemoteTag(initial, "changed"));
        assertThrows(IllegalStateException.class, () -> guard.bindRemoteTag(
                request("wrong-call", "SUBSCRIBE", "z9hG4bK-sub", null), "remote"));
        assertThrows(IllegalArgumentException.class, () -> guard.bindRemoteTag(initial, ""));
        assertThrows(IllegalArgumentException.class, () -> guard.bindRemoteTag(initial, "x".repeat(129)));
        assertTrue(guard.processResponse(valid, channel(HOST, PORT, "TCP")));
        assertTrue(guard.processResponse(response(other, 200), channel(HOST, PORT, "UDP")));
        guard.forget(initial);
        assertThrows(IllegalStateException.class, () -> guard.bindRemoteTag(initial, "remote"));
    }

    private static Request request(String callId, String method, String branch, String toTag) throws Exception {
        return SipFactory.getInstance().createMessageFactory().createRequest(method + " sip:device@192.0.2.10 SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP 192.0.2.1:5060;branch=" + branch + "\r\n"
                + "From: <sip:platform@192.0.2.1>;tag=local-tag\r\n"
                + "To: <sip:device@192.0.2.10>" + (toTag == null ? "" : ";tag=" + toTag) + "\r\n"
                + "Call-ID: " + callId + "\r\nCSeq: 1 " + method + "\r\nMax-Forwards: 70\r\n"
                + "Content-Length: 0\r\n\r\n");
    }

    private static Response response(Request request, int status) throws Exception {
        return SipFactory.getInstance().createMessageFactory().createResponse(status, request);
    }

    private static MessageChannel channel(String host, int port, String transport) throws Exception {
        return new TestChannel(InetAddress.getByName(host), port, host, port, transport);
    }

    private static final class TestChannel extends MessageChannel {
        private final InetAddress packetAddress;
        private final int packetPort;
        private final String peerAddress;
        private final int peerPort;
        private final String transport;

        private TestChannel(InetAddress packetAddress, int packetPort, String peerAddress, int peerPort, String transport) {
            this.packetAddress = packetAddress;
            this.packetPort = packetPort;
            this.peerAddress = peerAddress;
            this.peerPort = peerPort;
            this.transport = transport;
        }

        @Override public InetAddress getPeerPacketSourceAddress() { return packetAddress; }
        @Override public int getPeerPacketSourcePort() { return packetPort; }
        @Override public String getPeerAddress() { return peerAddress; }
        @Override public int getPeerPort() { return peerPort; }
        @Override public String getTransport() { return transport; }
        @Override public boolean isReliable() { return "TCP".equals(transport); }
        @Override public boolean isSecure() { return false; }
        @Override public void close() { }
        @Override public SIPTransactionStack getSIPStack() { return null; }
        @Override protected InetAddress getPeerInetAddress() { return packetAddress; }
        @Override protected String getPeerProtocol() { return transport; }
        @Override public String getKey() { return "test"; }
        @Override public String getViaHost() { return HOST; }
        @Override public int getViaPort() { return PORT; }
        @Override public void sendMessage(SIPMessage message) { throw new AssertionError("unexpected send"); }
        @Override protected void sendMessage(byte[] bytes, InetAddress address, int port, boolean retry) {
            throw new AssertionError("unexpected send");
        }
    }
}

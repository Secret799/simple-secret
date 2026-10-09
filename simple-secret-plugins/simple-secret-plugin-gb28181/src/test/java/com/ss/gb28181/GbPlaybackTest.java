package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static com.ss.gb28181.Gb28181ServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GbPlaybackTest {
    static final String CHANNEL = "34020000001320000002";
    final String password = UUID.randomUUID().toString();

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void establishesRetransmitsAckAndStopsOnOriginalSignalingConnection(String transport) throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, transport)) {
            peer.register(password);
            var target = new GbRtpTarget("127.0.0.1", 30000, GbRtpTarget.Transport.UDP);
            var future = server.play(DEVICE, CHANNEL, target);
            var invite = receiveMethod(peer, "INVITE");
            assertTrue(invite.start().startsWith("INVITE sip:" + CHANNEL + "@127.0.0.1:" + peer.port()));
            assertTrue(invite.headers().get("contact").contains(SERVER));
            assertTrue(invite.headers().get("subject").startsWith(CHANNEL + ":"));
            assertTrue(invite.body().contains("m=video 30000 RTP/AVP 96\r\n"));
            assertFalse(future.isDone());
            respond(peer, invite, 180, "", "remote-tag");
            assertFalse(future.isDone());
            respond(peer, invite, 200, answer(invite), "remote-tag");
            var ack = receiveMethod(peer, "ACK");
            var session = future.get(2, TimeUnit.SECONDS);
            assertEquals(CHANNEL, session.channelId());
            assertEquals(DEVICE, session.deviceId());
            assertEquals(target, session.target());
            assertEquals(invite.headers().get("call-id"), session.callId());
            assertEquals(invite.headers().get("cseq").split(" ")[0] + " ACK", ack.headers().get("cseq"));
            assertFalse(session.completion().toCompletableFuture().isDone());
            respond(peer, invite, 200, answer(invite), "remote-tag");
            assertEquals(ack.headers().get("to"), receiveMethod(peer, "ACK").headers().get("to"));
            var stopped = session.stop();
            var bye = receiveMethod(peer, "BYE");
            assertEquals(invite.headers().get("call-id"), bye.headers().get("call-id"));
            respond(peer, bye, 200, "", "remote-tag");
            stopped.get(2, TimeUnit.SECONDS);
            session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            session.stop().get(1, TimeUnit.SECONDS);
            session.close();
        }
    }

    @Test void cancelsEarlyInviteAndCleansLateSuccess() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var future = server.play(DEVICE, CHANNEL, target());
            var invite = receiveMethod(peer, "INVITE");
            assertTrue(future.cancel(false));
            respond(peer, invite, 180, "", "remote-tag");
            var cancel = receiveMethod(peer, "CANCEL");
            assertEquals(invite.headers().get("via"), cancel.headers().get("via"));
            respond(peer, cancel, 200, "", "remote-tag");
            respond(peer, invite, 200, answer(invite), "remote-tag");
            receiveMethod(peer, "ACK");
            var bye = receiveMethod(peer, "BYE");
            respond(peer, bye, 200, "", "remote-tag");
            assertTrue(future.isCancelled());
        }
    }

    @Test void rejectsInvalidSdpButAcknowledgesAndTerminatesAcceptedDialog() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var future = server.play(DEVICE, CHANNEL, target());
            var invite = receiveMethod(peer, "INVITE");
            respond(peer, invite, 200, answer(invite).replace("PS/90000", "H264/90000"), "remote-tag");
            receiveMethod(peer, "ACK");
            var bye = receiveMethod(peer, "BYE");
            assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            respond(peer, bye, 200, "", "remote-tag");
        }
    }

    @Test void rejectsNonSuccessAndBoundsSessionsIncludingLateResponseCleanup() throws Exception {
        int port = freePort();
        var opts = options(port).maxPlaySessions(1).inviteTimeout(Duration.ofMillis(200)).build();
        try (var server = Gb28181Server.open(opts, id -> Optional.of(password)); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var future = server.play(DEVICE, CHANNEL, target());
            var invite = receiveMethod(peer, "INVITE");
            assertThrows(IllegalStateException.class, () -> server.play(DEVICE, CHANNEL, target()));
            respond(peer, invite, 486, "", "remote-tag");
            assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.play(DEVICE, CHANNEL, target()));
        }
    }

    @Test void ignoresSpoofedSuccessAndRejectsWrongDialogBye() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP"); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var future = server.play(DEVICE, CHANNEL, target());
            var invite = receiveMethod(peer, "INVITE");
            respond(spoof, invite, 200, answer(invite), "remote-tag");
            peer.message("<Notify><CmdType>Keepalive</CmdType><SN>1</SN><DeviceID>" + DEVICE + "</DeviceID><Status>OK</Status></Notify>");
            assertEquals(200, peer.receive().status());
            assertFalse(future.isDone());
            respond(peer, invite, 200, answer(invite), "remote-tag");
            receiveMethod(peer, "ACK");
            var session = future.get(2, TimeUnit.SECONDS);
            sendBye(spoof, invite, "remote-tag", 3);
            assertEquals(481, spoof.receive().status());
            sendBye(peer, invite, "wrong-tag", 3);
            assertEquals(481, peer.receive().status());
            assertFalse(session.completion().toCompletableFuture().isDone());
            sendBye(peer, invite, "remote-tag", 4);
            assertEquals(200, peer.receive().status());
            session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    @Test void timesOutInviteAndByeAndFailsLiveSessionOnServerClose() throws Exception {
        int port = freePort();
        var opts = options(port).inviteTimeout(Duration.ofMillis(250)).stopTimeout(Duration.ofMillis(200)).build();
        try (var server = Gb28181Server.open(opts, id -> Optional.of(password)); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var pending = server.play(DEVICE, CHANNEL, target());
            var first = receiveMethod(peer, "INVITE");
            assertInstanceOf(TimeoutException.class, assertThrows(ExecutionException.class,
                    () -> pending.get(2, TimeUnit.SECONDS)).getCause());
            respond(peer, first, 200, answer(first), "remote-tag");
            receiveMethod(peer, "ACK");
            var lateBye = receiveMethod(peer, "BYE");
            respond(peer, lateBye, 200, "", "remote-tag");
            var active = server.play(DEVICE, CHANNEL, target());
            var second = receiveMethod(peer, "INVITE");
            respond(peer, second, 200, answer(second), "remote-tag");
            receiveMethod(peer, "ACK");
            var session = active.get(2, TimeUnit.SECONDS);
            var stop = session.stop();
            receiveMethod(peer, "BYE");
            assertInstanceOf(TimeoutException.class, assertThrows(ExecutionException.class,
                    () -> stop.get(2, TimeUnit.SECONDS)).getCause());
            var another = server.play(DEVICE, CHANNEL, target());
            var third = receiveMethod(peer, "INVITE");
            respond(peer, third, 200, answer(third), "remote-tag");
            receiveMethod(peer, "ACK");
            var live = another.get(2, TimeUnit.SECONDS);
            server.close();
            assertThrows(ExecutionException.class, () -> live.completion().toCompletableFuture().get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.play(DEVICE, CHANNEL, target()));
        }
    }

    @Test void validatesLimitsAndRejectsOfflineDevice() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> options(5060).maxPlaySessions(0).build());
        assertThrows(IllegalArgumentException.class, () -> options(5060).inviteTimeout(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class, () -> options(5060).stopTimeout(Duration.ZERO).build());
        try (var server = open(freePort())) {
            assertThrows(IllegalStateException.class, () -> server.play(DEVICE, CHANNEL, target()));
            assertThrows(IllegalArgumentException.class, () -> server.play(DEVICE, "bad\r\n", target()));
        }
    }

    @Test void spoofedFailureCannotPoisonInviteTransaction() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP"); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var future = server.play(DEVICE, CHANNEL, target());
            var invite = receiveMethod(peer, "INVITE");
            respond(spoof, invite, 486, "", "remote-tag");
            // Allow independent UDP workers to process the forged packet before the real answer.
            spoof.udp.setSoTimeout(150);
            assertThrows(java.net.SocketTimeoutException.class, spoof::receive);
            respond(peer, invite, 200, answer(invite), "remote-tag");
            var session = future.get(2, TimeUnit.SECONDS);
            assertEquals(CHANNEL, session.channelId());
        }
    }

    @Test void callerFutureTimeoutStillCancelsProvisionalInvite() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var future = server.play(DEVICE, CHANNEL, target()).orTimeout(150, TimeUnit.MILLISECONDS);
            var invite = receiveMethod(peer, "INVITE");
            respond(peer, invite, 180, "", "remote-tag");
            assertThrows(ExecutionException.class, () -> future.get(1, TimeUnit.SECONDS));
            receiveMethod(peer, "CANCEL");
        }
    }

    @Test void negotiatesPassiveTcpMediaOverUdpSignaling() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var target = new GbRtpTarget("127.0.0.1", 50000, GbRtpTarget.Transport.TCP_PASSIVE);
            var future = server.play(DEVICE, CHANNEL, target);
            var invite = receiveMethod(peer, "INVITE");
            assertTrue(invite.body().contains("m=video 50000 TCP/RTP/AVP 96\r\n"));
            assertTrue(invite.body().contains("a=setup:passive\r\n"));
            String answer = answer(invite).replace("RTP/AVP", "TCP/RTP/AVP")
                    + "a=setup:active\r\na=connection:new\r\n";
            respond(peer, invite, 200, answer, "remote-tag");
            receiveMethod(peer, "ACK");
            assertEquals(target, future.get(2, TimeUnit.SECONDS).target());
        }
    }

    @Test void offlineDeviceFailsSessionAndSendsBye() throws Exception {
        int port = freePort();
        var opts = options(port).heartbeatInterval(Duration.ofMillis(500)).heartbeatMisses(1).build();
        try (var server = Gb28181Server.open(opts, id -> Optional.of(password)); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var future = server.play(DEVICE, CHANNEL, target());
            var invite = receiveMethod(peer, "INVITE");
            respond(peer, invite, 200, answer(invite), "remote-tag");
            receiveMethod(peer, "ACK");
            var session = future.get(2, TimeUnit.SECONDS);
            receiveMethod(peer, "BYE");
            assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertTrue(server.devices().isEmpty());
        }
    }

    Gb28181Server open(int port) { return Gb28181Server.open(options(port).build(), id -> Optional.of(password)); }
    static GbRtpTarget target() { return new GbRtpTarget("127.0.0.1", 30000, GbRtpTarget.Transport.UDP); }
    static String answer(Wire invite) {
        return "v=0\r\no=" + CHANNEL + " 0 0 IN IP4 127.0.0.1\r\ns=Play\r\nc=IN IP4 127.0.0.1\r\nt=0 0\r\n"
                + "m=video 31000 RTP/AVP 96\r\na=sendonly\r\na=rtpmap:96 PS/90000\r\ny="
                + match(invite.body(), "y=([0-9]{10})") + "\r\n";
    }
    static void respond(Peer peer, Wire request, int status, String body, String remoteTag) throws IOException {
        StringBuilder b = new StringBuilder("SIP/2.0 " + status + " Test\r\n");
        for (String h : List.of("via", "from", "to", "call-id", "cseq")) {
            String value = request.headers().get(h);
            if (h.equals("to") && !value.contains(";tag=")) value += ";tag=" + remoteTag;
            b.append(h).append(": ").append(value).append("\r\n");
        }
        // Deliberately unusable Contact: dialog signaling must keep using the registered source.
        b.append("Contact: <sip:").append(CHANNEL).append("@192.0.2.1:5099>\r\n");
        if (!body.isEmpty()) b.append("Content-Type: application/sdp\r\n");
        peer.send(b + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
    }
    static Wire receiveMethod(Peer peer, String method) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        do {
            Wire packet = peer.receive();
            if (packet.start().startsWith(method + " ")) return packet;
        } while (System.nanoTime() < deadline);
        throw new IOException("Missing " + method);
    }
    static void sendBye(Peer peer, Wire invite, String tag, long cseq) throws IOException {
        peer.send("BYE sip:" + SERVER + "@" + REALM + " SIP/2.0\r\nVia: SIP/2.0/" + peer.transport
                + " 127.0.0.1:" + peer.port() + ";rport;branch=z9hG4bK" + UUID.randomUUID()
                + "\r\nFrom: " + invite.headers().get("to") + ";tag=" + tag
                + "\r\nTo: " + invite.headers().get("from") + "\r\nCall-ID: " + invite.headers().get("call-id")
                + "\r\nCSeq: " + cseq + " BYE\r\nMax-Forwards: 70\r\nContent-Length: 0\r\n\r\n");
    }
}

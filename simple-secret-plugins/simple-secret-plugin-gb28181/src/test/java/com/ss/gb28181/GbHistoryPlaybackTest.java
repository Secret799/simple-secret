package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static com.ss.gb28181.Gb28181ServerTest.*;
import static com.ss.gb28181.GbPlaybackTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GbHistoryPlaybackTest {
    final String password = UUID.randomUUID().toString();
    static final GbPlaybackRange RANGE = new GbPlaybackRange(Instant.ofEpochSecond(1700000000), Instant.ofEpochSecond(1700000060));

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void negotiatesHistoryControlsAndStopsWithMonotonicSipCseq(String transport) throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, transport)) {
            peer.register(password);
            var opening = server.playback(DEVICE, CHANNEL, target(), RANGE);
            var invite = receiveMethod(peer, "INVITE");
            assertTrue(invite.body().contains("s=Playback\r\n"));
            assertTrue(invite.body().contains("u=" + CHANNEL + ":0\r\n"));
            assertTrue(invite.body().contains("t=1700000000 1700000060\r\n"));
            assertTrue(match(invite.body(), "y=([0-9]{10})").startsWith("1"));
            respond(peer, invite, 200, historyAnswer(invite), "remote-tag");
            receiveMethod(peer, "ACK");
            var session = opening.get(2, TimeUnit.SECONDS);
            assertEquals(Optional.of(RANGE), session.playbackRange());
            int seq = 1;
            for (var command : List.of(GbPlaybackControl.pause(), GbPlaybackControl.resume(),
                    GbPlaybackControl.seek(Duration.ofSeconds(12)), GbPlaybackControl.speed(2))) {
                var result = session.control(command);
                var info = receiveMethod(peer, "INFO");
                assertEquals(++seq + " INFO", info.headers().get("cseq"));
                assertEquals("application/mansrtsp", info.headers().get("content-type").toLowerCase());
                assertTrue(info.body().contains("CSeq: " + (seq - 1) + "\r\n"));
                assertTrue(info.body().endsWith("\r\n\r\n"));
                assertThrows(IllegalStateException.class, () -> session.control(GbPlaybackControl.pause()));
                controlResponse(peer, info, 200, "RTSP/1.0 200 OK\r\nCSeq: " + (seq - 1) + "\r\n\r\n");
                result.get(2, TimeUnit.SECONDS);
            }
            assertThrows(IllegalArgumentException.class, () -> session.control(GbPlaybackControl.seek(Duration.ofSeconds(60))));
            var stop = session.stop();
            var bye = receiveMethod(peer, "BYE");
            assertEquals("6 BYE", bye.headers().get("cseq"));
            respond(peer, bye, 200, "", "remote-tag");
            stop.get(2, TimeUnit.SECONDS);
            assertThrows(IllegalStateException.class, () -> session.control(GbPlaybackControl.resume()));
        }
    }

    @Test void releasesOnlyControlOnCancelExternalCompletionTimeoutAndRejection() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).queryTimeout(Duration.ofMillis(300)).maxPlaySessions(1)
                .maxPendingQueries(1).build(), id -> Optional.of(password)); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var established = establish(server, peer);
            var session = established.session();
            // Repeated controls must not retain guard entries or RI transactions.
            for (int i = 0; i < 12; i++) {
                var result = session.control(GbPlaybackControl.pause());
                var info = receiveMethod(peer, "INFO");
                if (i % 3 == 0) result.cancel(false);
                else if (i % 3 == 1) result.complete(null);
                else { controlResponse(peer, info, 500, ""); assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS)); }
            }
            var timed = session.control(GbPlaybackControl.resume());
            receiveMethod(peer, "INFO");
            assertInstanceOf(TimeoutException.class, assertThrows(ExecutionException.class,
                    () -> timed.get(2, TimeUnit.SECONDS)).getCause());
            assertFalse(session.completion().toCompletableFuture().isDone());
            // INVITE expectation must survive individual INFO cleanup.
            respond(peer, established.invite(), 200, historyAnswer(established.invite()), "remote-tag");
            receiveMethod(peer, "ACK");
            var last = session.control(GbPlaybackControl.resume());
            var info = receiveMethod(peer, "INFO");
            controlResponse(peer, info, 200, "");
            last.get(2, TimeUnit.SECONDS);
            var pending = session.control(GbPlaybackControl.pause());
            receiveMethod(peer, "INFO");
            session.stop();
            receiveMethod(peer, "BYE");
            assertThrows(ExecutionException.class, () -> pending.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void ignoresForgedInfoResponsesBeforeTransactionAdvancesAndRejectsInvalidRtsp() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP"); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var session = establish(server, peer).session();
            var result = session.control(GbPlaybackControl.pause());
            var info = receiveMethod(peer, "INFO");
            controlResponse(spoof, info, 481, "");
            for (String field : List.of("to", "from", "cseq", "via")) {
                var headers = new HashMap<>(info.headers());
                headers.put(field, switch (field) {
                    case "cseq" -> "900 INFO";
                    case "via" -> headers.get(field).replace("branch=", "branch=forged");
                    default -> headers.get(field) + "forged";
                });
                controlResponse(peer, new Wire(info.start(), headers, info.body()), 481, "");
            }
            // A request/response round trip provides a deterministic processing barrier on the real peer.
            peer.message("<Notify><CmdType>Keepalive</CmdType><SN>1</SN><DeviceID>" + DEVICE + "</DeviceID><Status>OK</Status></Notify>");
            assertEquals(200, peer.receive().status());
            assertFalse(result.isDone());
            controlResponse(peer, info, 200, "RTSP/1.0 200 OK\r\nCSeq: 99\r\n\r\n");
            assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertFalse(session.completion().toCompletableFuture().isDone());
            var rejected = session.control(GbPlaybackControl.resume());
            var reentrant = rejected.handle((unused, failure) -> {
                assertThrows(IllegalStateException.class, () -> session.control(GbPlaybackControl.pause()));
                session.stop(); // Reentrant stop must not send a second BYE.
                return null;
            });
            controlResponse(peer, receiveMethod(peer, "INFO"), 481, "");
            assertThrows(ExecutionException.class, () -> rejected.get(2, TimeUnit.SECONDS));
            reentrant.get(2, TimeUnit.SECONDS);
            receiveMethod(peer, "BYE");
            assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void acceptsOnlyMatchingHistoricalMediaEndAndWaitsForBye(String transport) throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, transport); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var established = establish(server, peer);
            var session = established.session();
            notifyEnd(spoof, established.invite(), CHANNEL, "remote-tag", 3);
            assertEquals(481, spoof.receive().status());
            notifyEnd(peer, established.invite(), DEVICE, "remote-tag", 3);
            assertEquals(481, peer.receive().status());
            notifyEnd(peer, established.invite(), CHANNEL, "wrong", 3);
            assertEquals(481, peer.receive().status());
            assertFalse(session.completion().toCompletableFuture().isDone());
            notifyEnd(peer, established.invite(), CHANNEL, "remote-tag", 4);
            assertEquals(200, peer.receive().status());
            var bye = receiveMethod(peer, "BYE");
            assertFalse(session.completion().toCompletableFuture().isDone());
            notifyEnd(peer, established.invite(), CHANNEL, "remote-tag", 4);
            assertEquals(500, peer.receive().status());
            respond(peer, bye, 200, "", "remote-tag");
            session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    @Test void offlineDeviceClearsPendingControlAndEndsHistoricalSession() throws Exception {
        int port = freePort();
        var opts = options(port).heartbeatInterval(Duration.ofMillis(750)).heartbeatMisses(1).build();
        try (var server = Gb28181Server.open(opts, id -> Optional.of(password)); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var session = establish(server, peer).session();
            var control = session.control(GbPlaybackControl.pause());
            receiveMethod(peer, "INFO");
            receiveMethod(peer, "BYE");
            assertThrows(ExecutionException.class, () -> control.get(2, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertTrue(server.devices().isEmpty());
        }
    }

    @Test void callerCompletionContinuationCanImmediatelyStartNextControl() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var session = establish(server, peer).session();
            var first = session.control(GbPlaybackControl.pause());
            receiveMethod(peer, "INFO");
            var next = first.thenApply(unused -> session.control(GbPlaybackControl.resume()));
            first.complete(null);
            var second = next.get(2, TimeUnit.SECONDS);
            controlResponse(peer, receiveMethod(peer, "INFO"), 200, "");
            second.get(2, TimeUnit.SECONDS);
        }
    }

    @Test void rejectsUntrustedDialogBeforeParsingXml() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP"); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var established = establish(server, peer);
            String malformed = "<!DOCTYPE Notify [<!ENTITY x SYSTEM 'file:///not-readable'>]><Notify>&x;</Notify>";
            spoof.message(malformed);
            assertEquals(403, spoof.receive().status());
            notifyBody(spoof, established.invite(), "remote-tag", 5, malformed);
            assertEquals(481, spoof.receive().status());
            notifyBody(peer, established.invite(), "wrong-tag", 5, malformed);
            assertEquals(481, peer.receive().status());
            var unknownHeaders = new HashMap<>(established.invite().headers());
            unknownHeaders.put("call-id", "unknown-call");
            notifyBody(peer, new Wire(established.invite().start(), unknownHeaders, ""), "remote-tag", 5, malformed);
            assertEquals(481, peer.receive().status());
            assertFalse(established.session().completion().toCompletableFuture().isDone());
        }
    }

    @Test void lateTimeoutForRetiredInfoCannotAffectNextControl() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var session = establish(server, peer).session();
            var first = session.control(GbPlaybackControl.pause());
            receiveMethod(peer, "INFO");
            var playbackField = Gb28181Server.class.getDeclaredField("playback");
            playbackField.setAccessible(true);
            Object playback = playbackField.get(server);
            var callsField = GbPlayback.class.getDeclaredField("calls");
            callsField.setAccessible(true);
            Object call = ((java.util.Map<?, ?>) callsField.get(playback)).get(session.callId());
            var controlField = call.getClass().getDeclaredField("control");
            controlField.setAccessible(true);
            Object pending = controlField.get(call);
            var transactionField = pending.getClass().getDeclaredField("transaction");
            transactionField.setAccessible(true);
            var retired = (javax.sip.ClientTransaction) transactionField.get(pending);
            first.cancel(false);
            var second = session.control(GbPlaybackControl.resume());
            var info = receiveMethod(peer, "INFO");
            server.processTimeout(new javax.sip.TimeoutEvent(server, retired, javax.sip.Timeout.TRANSACTION));
            assertFalse(second.isDone());
            assertFalse(session.completion().toCompletableFuture().isDone());
            controlResponse(peer, info, 200, "");
            second.get(2, TimeUnit.SECONDS);
        }
    }

    @Test void rejectsLiveControlAndLiveEndAndClearsHistoryControlOnClose() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.play(DEVICE, CHANNEL, target());
            var invite = receiveMethod(peer, "INVITE");
            respond(peer, invite, 200, answer(invite), "remote-tag");
            receiveMethod(peer, "ACK");
            var live = opening.get(2, TimeUnit.SECONDS);
            assertTrue(live.playbackRange().isEmpty());
            assertThrows(IllegalStateException.class, () -> live.control(GbPlaybackControl.pause()));
            notifyEnd(peer, invite, CHANNEL, "remote-tag", 4);
            assertEquals(481, peer.receive().status());
            var session = establish(server, peer).session();
            var control = session.control(GbPlaybackControl.pause());
            receiveMethod(peer, "INFO");
            server.close();
            assertThrows(ExecutionException.class, () -> control.get(2, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
        }
    }

    Gb28181Server open(int port) { return Gb28181Server.open(options(port).build(), id -> Optional.of(password)); }
    static Established establish(Gb28181Server server, Peer peer) throws Exception {
        var future = server.playback(DEVICE, CHANNEL, target(), RANGE);
        var invite = receiveMethod(peer, "INVITE");
        respond(peer, invite, 200, historyAnswer(invite), "remote-tag");
        receiveMethod(peer, "ACK");
        return new Established(future.get(2, TimeUnit.SECONDS), invite);
    }
    static String historyAnswer(Wire invite) {
        return answer(invite).replace("s=Play\r\n", "s=Playback\r\nu=" + CHANNEL + ":0\r\n")
                .replace("t=0 0", "t=1700000000 1700000060");
    }
    static void controlResponse(Peer peer, Wire request, int status, String body) throws IOException {
        var b = new StringBuilder("SIP/2.0 " + status + " Test\r\n");
        for (String h : List.of("via", "from", "to", "call-id", "cseq")) b.append(h).append(": ").append(request.headers().get(h)).append("\r\n");
        if (!body.isEmpty()) b.append("Content-Type: Application/MANSRTSP\r\n");
        peer.send(b + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
    }
    static void notifyEnd(Peer peer, Wire invite, String channel, String tag, long cseq) throws IOException {
        String body = "<Notify><CmdType>MediaStatus</CmdType><SN>1</SN><DeviceID>" + channel
                + "</DeviceID><NotifyType>121</NotifyType></Notify>";
        notifyBody(peer, invite, tag, cseq, body);
    }
    static void notifyBody(Peer peer, Wire invite, String tag, long cseq, String body) throws IOException {
        peer.send("MESSAGE sip:" + SERVER + "@" + REALM + " SIP/2.0\r\nVia: SIP/2.0/" + peer.transport
                + " 127.0.0.1:" + peer.port() + ";rport;branch=z9hG4bK" + UUID.randomUUID()
                + "\r\nFrom: " + invite.headers().get("to") + ";tag=" + tag
                + "\r\nTo: " + invite.headers().get("from") + "\r\nCall-ID: " + invite.headers().get("call-id")
                + "\r\nCSeq: " + cseq + " MESSAGE\r\nMax-Forwards: 70\r\nContent-Type: Application/MANSCDP+xml\r\nContent-Length: "
                + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
    }
    record Established(GbPlaySession session, Wire invite) { }
}

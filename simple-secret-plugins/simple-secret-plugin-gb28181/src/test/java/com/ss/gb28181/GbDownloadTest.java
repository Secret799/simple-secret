package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import static com.ss.gb28181.Gb28181ServerTest.*;
import static com.ss.gb28181.GbPlaybackTest.*;
import static com.ss.gb28181.GbHistoryPlaybackTest.RANGE;
import static com.ss.gb28181.GbHistoryPlaybackTest.notifyEnd;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GbDownloadTest {
    final String password = UUID.randomUUID().toString();
    static final GbDownloadRequest REQUEST = new GbDownloadRequest(RANGE, 8);

    @ParameterizedTest
    @CsvSource({"UDP,UDP", "UDP,TCP_PASSIVE", "TCP,UDP", "TCP,TCP_PASSIVE"})
    void negotiatesDownloadOverEitherSignalingAndMediaTransport(String signaling, String media) throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, signaling)) {
            peer.register(password);
            var target = new GbRtpTarget("127.0.0.1", 30000, GbRtpTarget.Transport.valueOf(media));
            var opening = server.download(DEVICE, CHANNEL, target, REQUEST);
            var invite = receiveMethod(peer, "INVITE");
            assertTrue(invite.start().startsWith("INVITE sip:" + CHANNEL + "@127.0.0.1:" + peer.port()));
            assertTrue(invite.body().contains("s=Download\r\n"));
            assertTrue(invite.body().contains("u=" + CHANNEL + ":0\r\n"));
            assertTrue(invite.body().contains("t=1700000000 1700000060\r\n"));
            assertTrue(invite.body().contains("a=downloadspeed:8\r\n"));
            assertTrue(match(invite.body(), "y=([0-9]{10})").startsWith("1"));
            String protocol = target.transport() == GbRtpTarget.Transport.UDP ? "RTP/AVP" : "TCP/RTP/AVP";
            assertTrue(invite.body().contains("m=video 30000 " + protocol + " 96\r\n"));
            if (target.transport() == GbRtpTarget.Transport.TCP_PASSIVE)
                assertTrue(invite.body().contains("a=setup:passive\r\n"));
            String answer = downloadAnswer(invite, target) + "a=filesize:12345678901\r\na=downloadspeed:4\r\n";
            respond(peer, invite, 200, answer, "remote-tag");
            var ack = receiveMethod(peer, "ACK");
            var session = opening.get(2, TimeUnit.SECONDS);
            assertEquals(DEVICE, session.deviceId());
            assertEquals(CHANNEL, session.channelId());
            assertEquals(target, session.target());
            assertEquals(invite.headers().get("call-id"), session.callId());
            assertEquals(Optional.of(RANGE), session.playbackRange());
            assertEquals(Optional.of(REQUEST), session.downloadRequest());
            assertEquals(OptionalLong.of(12345678901L), session.downloadFileSize());
            assertFalse(session.completion().toCompletableFuture().isDone());
            respond(peer, invite, 200, answer, "remote-tag");
            assertEquals(ack.headers().get("to"), receiveMethod(peer, "ACK").headers().get("to"));
            for (var control : List.of(GbPlaybackControl.pause(), GbPlaybackControl.resume(),
                    GbPlaybackControl.seek(Duration.ofSeconds(12)), GbPlaybackControl.speed(2)))
                assertThrows(IllegalStateException.class, () -> session.control(control));
            var stop = session.stop();
            // Any wrongly emitted INFO is a failure, rather than skipped by receiveMethod.
            var bye = peer.receive();
            assertTrue(bye.start().startsWith("BYE "), bye.start());
            assertEquals("2 BYE", bye.headers().get("cseq"));
            respond(peer, bye, 200, "", "remote-tag");
            stop.get(2, TimeUnit.SECONDS);
            session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            session.stop().get(1, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"", "a=filesize:0\r\n"})
    void distinguishesMissingFileSizeFromZero(String size) throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.download(DEVICE, CHANNEL, target(), REQUEST);
            var invite = receiveMethod(peer, "INVITE");
            respond(peer, invite, 200, downloadAnswer(invite, target()) + size, "remote-tag");
            receiveMethod(peer, "ACK");
            assertEquals(size.isEmpty() ? OptionalLong.empty() : OptionalLong.of(0),
                    opening.get(2, TimeUnit.SECONDS).downloadFileSize());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"mode", "time", "size", "ssrc", "direction"})
    void invalidAnswerStillGetsAckByeAndRetainsCleanupCapacity(String invalid) throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).maxPlaySessions(1).build(), id -> Optional.of(password));
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.download(DEVICE, CHANNEL, target(), REQUEST);
            var invite = receiveMethod(peer, "INVITE");
            String good = downloadAnswer(invite, target());
            String bad = switch (invalid) {
                case "mode" -> good.replace("s=Download", "s=Playback");
                case "time" -> good.replace("1700000060", "1700000061");
                case "size" -> good + "a=filesize:-1\r\n";
                case "ssrc" -> good.replace(match(good, "y=([0-9]{10})"), "1999999999");
                default -> good.replace("a=sendonly", "a=recvonly");
            };
            respond(peer, invite, 200, bad, "remote-tag");
            receiveMethod(peer, "ACK");
            var bye = receiveMethod(peer, "BYE");
            assertInstanceOf(IllegalArgumentException.class,
                    assertThrows(ExecutionException.class, () -> opening.get(2, TimeUnit.SECONDS)).getCause());
            respond(peer, bye, 200, "", "remote-tag");
            assertThrows(IllegalStateException.class, () -> server.play(DEVICE, CHANNEL, target()));
            assertThrows(IllegalStateException.class, () -> server.playback(DEVICE, CHANNEL, target(), RANGE));
            assertThrows(IllegalStateException.class, () -> server.download(DEVICE, CHANNEL, target(), REQUEST));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void canceledDownloadCleansLateSuccess(String transport) throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, transport)) {
            peer.register(password);
            var opening = server.download(DEVICE, CHANNEL, target(), REQUEST);
            var invite = receiveMethod(peer, "INVITE");
            assertTrue(opening.cancel(false));
            respond(peer, invite, 180, "", "remote-tag");
            var cancel = receiveMethod(peer, "CANCEL");
            assertEquals(invite.headers().get("via"), cancel.headers().get("via"));
            respond(peer, cancel, 200, "", "remote-tag");
            respond(peer, invite, 200, downloadAnswer(invite, target()), "remote-tag");
            receiveMethod(peer, "ACK");
            var bye = receiveMethod(peer, "BYE");
            respond(peer, bye, 200, "", "remote-tag");
            assertTrue(opening.isCancelled());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void onlyMatchingMediaEndTerminatesDownload(String transport) throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, transport); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var established = establish(server, peer);
            var session = established.session();
            var invite = established.invite();
            notifyEnd(spoof, invite, CHANNEL, "remote-tag", 3);
            assertEquals(481, spoof.receive().status());
            notifyEnd(peer, invite, CHANNEL, "wrong", 3);
            assertEquals(481, peer.receive().status());
            notifyEnd(peer, invite, DEVICE, "remote-tag", 3);
            assertEquals(481, peer.receive().status());
            var headers = new HashMap<>(invite.headers());
            headers.put("from", headers.get("from").replace(";tag=", ";tag=wrong"));
            notifyEnd(peer, new Wire(invite.start(), headers, invite.body()), CHANNEL, "remote-tag", 3);
            assertEquals(481, peer.receive().status());
            headers = new HashMap<>(invite.headers());
            headers.put("call-id", "unknown-download");
            notifyEnd(peer, new Wire(invite.start(), headers, invite.body()), CHANNEL, "remote-tag", 3);
            assertEquals(481, peer.receive().status());
            assertFalse(session.completion().toCompletableFuture().isDone());
            notifyEnd(peer, invite, CHANNEL, "remote-tag", 4);
            assertEquals(200, peer.receive().status());
            var bye = receiveMethod(peer, "BYE");
            assertFalse(session.completion().toCompletableFuture().isDone());
            notifyEnd(peer, invite, CHANNEL, "remote-tag", 4);
            assertEquals(500, peer.receive().status());
            notifyEnd(peer, invite, CHANNEL, "remote-tag", 5);
            assertEquals(200, peer.receive().status());
            respond(peer, bye, 200, "", "remote-tag");
            session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    @Test void sharesCapacityWithLiveHistoryAndFailsAllSessionsOnClose() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).maxPlaySessions(3).build(), id -> Optional.of(password));
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var download = establish(server, peer).session();
            var history = GbHistoryPlaybackTest.establish(server, peer).session();
            var opening = server.play(DEVICE, CHANNEL, target());
            var invite = receiveMethod(peer, "INVITE");
            assertTrue(invite.body().contains("s=Play\r\n"));
            assertFalse(invite.body().contains("downloadspeed"));
            respond(peer, invite, 200, answer(invite), "remote-tag");
            receiveMethod(peer, "ACK");
            var live = opening.get(2, TimeUnit.SECONDS);
            assertTrue(live.playbackRange().isEmpty());
            for (var session : List.of(live, history)) {
                assertTrue(session.downloadRequest().isEmpty());
                assertTrue(session.downloadFileSize().isEmpty());
            }
            assertEquals(Optional.of(RANGE), history.playbackRange());
            assertNotEquals(download.ssrc(), history.ssrc());
            assertThrows(IllegalStateException.class, () -> server.download(DEVICE, CHANNEL, target(), REQUEST));
            assertThrows(IllegalStateException.class, () -> server.play(DEVICE, CHANNEL, target()));
            assertThrows(IllegalStateException.class, () -> server.playback(DEVICE, CHANNEL, target(), RANGE));
            server.close();
            for (var session : List.of(live, history, download))
                assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.download(DEVICE, CHANNEL, target(), REQUEST));
        }
    }

    @Test void offlineDeviceEndsDownloadAndSendsBye() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).heartbeatInterval(Duration.ofMillis(750))
                .heartbeatMisses(1).build(), id -> Optional.of(password)); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var session = establish(server, peer).session();
            receiveMethod(peer, "BYE");
            assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertTrue(server.devices().isEmpty());
            assertThrows(IllegalStateException.class, () -> server.download(DEVICE, CHANNEL, target(), REQUEST));
        }
    }

    @Test void rejectsInvalidOrOfflineDownloadBeforeInvite() throws Exception {
        try (var server = open(freePort())) {
            assertThrows(NullPointerException.class, () -> server.download(DEVICE, CHANNEL, target(), null));
            assertThrows(NullPointerException.class, () -> server.download(DEVICE, CHANNEL, null, REQUEST));
            assertThrows(IllegalArgumentException.class, () -> server.download(DEVICE, "bad\r\n", target(), REQUEST));
            assertThrows(IllegalStateException.class, () -> server.download(DEVICE, CHANNEL, target(), REQUEST));
        }
    }

    Gb28181Server open(int port) { return Gb28181Server.open(options(port).build(), id -> Optional.of(password)); }
    static GbHistoryPlaybackTest.Established establish(Gb28181Server server, Peer peer) throws Exception {
        var opening = server.download(DEVICE, CHANNEL, target(), REQUEST);
        var invite = receiveMethod(peer, "INVITE");
        respond(peer, invite, 200, downloadAnswer(invite, target()), "remote-tag");
        receiveMethod(peer, "ACK");
        return new GbHistoryPlaybackTest.Established(opening.get(2, TimeUnit.SECONDS), invite);
    }
    static String downloadAnswer(Wire invite, GbRtpTarget target) {
        String result = GbHistoryPlaybackTest.historyAnswer(invite).replace("s=Playback", "s=Download");
        if (target.transport() == GbRtpTarget.Transport.TCP_PASSIVE)
            result = result.replace("RTP/AVP", "TCP/RTP/AVP") + "a=setup:active\r\na=connection:new\r\n";
        return result;
    }
}

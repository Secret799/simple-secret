package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static com.ss.gb28181.Gb28181ServerTest.*;
import static com.ss.gb28181.GbPlaybackTest.receiveMethod;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class GbAlarmSubscriptionTest {
    static final String TARGET = "3402000001";
    final String password = UUID.randomUUID().toString();
    static GbAlarmSubscriptionRequest request() { return GbAlarmSubscriptionRequest.all(Duration.ofSeconds(60)); }

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void subscribesReceivesOnlySipReceiptAndStopsOnRegisteredConnection(String transport) throws Exception {
        int port = freePort();
        var alarms = new LinkedBlockingQueue<GbAlarmEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), alarms::add);
             var peer = new Peer(port, transport)) {
            peer.register(password);
            var opening = server.subscribeAlarms(DEVICE, TARGET, request());
            Wire subscribe = receiveMethod(peer, "SUBSCRIBE");
            assertTrue(subscribe.start().startsWith("SUBSCRIBE sip:" + TARGET + "@127.0.0.1:" + peer.port()));
            assertEquals("presence", subscribe.headers().get("event"));
            assertEquals("60", subscribe.headers().get("expires"));
            assertTrue(subscribe.headers().get("contact").contains(SERVER));
            assertTrue(subscribe.body().contains("<CmdType>Alarm</CmdType>"));
            respond(peer, subscribe, 200, "Expires: 30\r\n", "remote");
            GbAlarmSubscription session = opening.get(2, TimeUnit.SECONDS);
            assertEquals(DEVICE, session.deviceId()); assertEquals(TARGET, session.targetId());
            assertEquals(request(), session.request()); assertEquals(subscribe.headers().get("call-id"), session.callId());
            notify(peer, subscribe, 1, "remote", "presence", "active;expires=30", alarm(TARGET, 71));
            Wire receipt = peer.receive(); assertEquals(200, receipt.status()); assertEquals("", receipt.body());
            assertEquals(71, alarms.poll(2, TimeUnit.SECONDS).alarm().sn());
            assertNoPacket(peer);
            var stopping = session.stop();
            var stop = receiveMethod(peer, "SUBSCRIBE");
            assertEquals("0", stop.headers().get("expires"));
            assertEquals(subscribe.headers().get("call-id"), stop.headers().get("call-id"));
            assertTrue(cseq(stop) > cseq(subscribe));
            assertTrue(stop.headers().get("to").contains(";tag=remote"));
            stopping.cancel(false);
            respond(peer, stop, 200, "", "remote");
            session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            session.stop().get(1, TimeUnit.SECONDS);
        }
    }

    @Test void acceptsEarlyNotifyWithoutCompletingOpeningAndBindsOnlyValidatedTag() throws Exception {
        int port = freePort(); var alarms = new LinkedBlockingQueue<GbAlarmEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), alarms::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.subscribeAlarms(DEVICE, TARGET, request()); var sub = peer.receive();
            notify(peer, sub, 1, "bad", "presence", "active", "<malformed");
            assertEquals(400, peer.receive().status());
            notify(peer, sub, 1, "remote", "presence", "pending;expires=20", alarm(TARGET, 1));
            assertEquals(200, peer.receive().status()); assertNotNull(alarms.poll(2, TimeUnit.SECONDS));
            assertFalse(opening.isDone());
            respond(peer, sub, 200, "Expires: 30\r\n", "remote");
            opening.get(2, TimeUnit.SECONDS);
        }
    }

    @Test void rejectsSourceTagsEventStateMimeXmlAndStaleCseqWithoutConsumingSequence() throws Exception {
        int port = freePort(); var alarms = new LinkedBlockingQueue<GbAlarmEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), alarms::add);
             var peer = new Peer(port, "UDP"); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.subscribeAlarms(DEVICE, TARGET, request()); var sub = peer.receive();
            respond(spoof, sub, 403, "", "remote"); assertNoPacket(spoof);
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); opening.get(2, TimeUnit.SECONDS);
            notify(spoof, sub, 9, "remote", "presence", "active", "<bad"); assertEquals(481, spoof.receive().status());
            notify(peer, sub, 9, "wrong", "presence", "active", "<bad"); assertEquals(481, peer.receive().status());
            notify(peer, sub, 9, "remote", "other", "active", "<bad"); assertEquals(489, peer.receive().status());
            notify(peer, sub, 9, "remote", "presence;id=1", "active", "<bad"); assertEquals(489, peer.receive().status());
            notify(peer, sub, 9, "remote", "presence", "active;expires=0", ""); assertEquals(400, peer.receive().status());
            notify(peer, sub, 9, "remote", "presence", "active", alarm(DEVICE, 1)); assertEquals(400, peer.receive().status());
            notify(peer, sub, 9, "remote", "presence", "active", alarm(TARGET, 2)); assertEquals(200, peer.receive().status());
            notify(peer, sub, 9, "remote", "presence", "active", alarm(TARGET, 3)); assertEquals(500, peer.receive().status());
            assertEquals(2, alarms.poll(2, TimeUnit.SECONDS).alarm().sn()); assertNull(alarms.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test void cancelledOpeningRetainsCapacityAndCleansLateSuccessWithoutDelivery() throws Exception {
        int port = freePort(); var alarms = new LinkedBlockingQueue<GbAlarmEvent>();
        try (var server = Gb28181Server.open(options(port).maxAlarmSubscriptions(1).build(), id -> Optional.of(password), alarms::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeAlarms(DEVICE, TARGET, request()); var sub = peer.receive();
            opening.cancel(false);
            assertThrows(IllegalStateException.class, () -> server.subscribeAlarms(DEVICE, TARGET, request()));
            respond(peer, sub, 200, "Expires: 60\r\n", "remote");
            var stop = receiveMethod(peer, "SUBSCRIBE"); assertEquals("0", stop.headers().get("expires"));
            respond(peer, stop, 200, "Expires: 0\r\n", "remote");
            notify(peer, sub, 1, "remote", "presence", "active", alarm(TARGET, 1)); assertEquals(200, peer.receive().status());
            assertNull(alarms.poll(150, TimeUnit.MILLISECONDS)); assertTrue(opening.isCancelled());
        }
    }

    @Test void renewsAtGrantedLifetimeWithIncreasingCseqAndFailsRejectedRenewal() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeAlarms(DEVICE, TARGET, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 1\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            notify(peer, sub, 1, "remote", "presence", "active", ""); assertEquals(200, peer.receive().status());
            var renewal = receiveMethod(peer, "SUBSCRIBE"); assertEquals("60", renewal.headers().get("expires"));
            assertTrue(cseq(renewal) > cseq(sub)); assertTrue(renewal.headers().get("to").contains(";tag=remote"));
            respond(peer, renewal, 403, "", "remote");
            assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals("0", receiveMethod(peer, "SUBSCRIBE").headers().get("expires"));
        }
    }

    @Test void terminalNotifyCompletesStopAndCleanupAcknowledgesWithoutCallback() throws Exception {
        int port = freePort(); var alarms = new LinkedBlockingQueue<GbAlarmEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), alarms::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeAlarms(DEVICE, TARGET, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            var stopped = session.stop(); var stop = peer.receive(); assertEquals("0", stop.headers().get("expires"));
            notify(peer, sub, 1, "remote", "presence", "terminated;reason=timeout", "");
            assertEquals(200, peer.receive().status()); stopped.get(2, TimeUnit.SECONDS);
            notify(peer, sub, 2, "remote", "presence", "active", alarm(TARGET, 1));
            assertEquals(200, peer.receive().status());
            assertNull(alarms.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test void notificationUsesSharedCallbackCapacityButNoMessageCapacity() throws Exception {
        int port = freePort(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var server = Gb28181Server.open(options(port).maxPendingQueries(1).maxPendingAlarms(1).build(),
                id -> Optional.of(password), event -> { entered.countDown(); try { release.await(3, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } });
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var query = server.queryCatalog(DEVICE); peer.respond(peer.receive(), 200);
            var opening = server.subscribeAlarms(DEVICE, TARGET, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); opening.get(2, TimeUnit.SECONDS);
            notify(peer, sub, 1, "remote", "presence", "active", alarm(TARGET, 1)); assertEquals(200, peer.receive().status());
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            notify(peer, sub, 2, "remote", "presence", "active", alarm(TARGET, 2)); assertEquals(503, peer.receive().status());
            release.countDown(); query.cancel(false);
        } finally { release.countDown(); }
    }

    @Test void rejectsMissingListenerOfflineAndInvalidIdsSynchronously() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password)); var peer = new Peer(port, "UDP")) {
            peer.register(password); assertThrows(IllegalStateException.class, () -> server.subscribeAlarms(DEVICE, TARGET, request()));
        }
        try (var server = Gb28181Server.open(options(freePort()).build(), id -> Optional.of(password), event -> { })) {
            assertThrows(IllegalArgumentException.class, () -> server.subscribeAlarms(DEVICE, "bad", request()));
            assertThrows(IllegalStateException.class, () -> server.subscribeAlarms(DEVICE, TARGET, request()));
        }
    }

    @Test void unansweredRenewalCannotExtendLeaseAndStopDuringRenewalCannotRevive() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).stopTimeout(Duration.ofMillis(300)).build(), id -> Optional.of(password), event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var first = server.subscribeAlarms(DEVICE, TARGET, request()); var initial = peer.receive();
            respond(peer, initial, 200, "Expires: 1\r\n", "remote"); var expiring = first.get(2, TimeUnit.SECONDS);
            notify(peer, initial, 1, "remote", "presence", "active", ""); assertEquals(200, peer.receive().status());
            var unanswered = receiveMethod(peer, "SUBSCRIBE"); assertEquals("60", unanswered.headers().get("expires"));
            assertInstanceOf(TimeoutException.class, assertThrows(ExecutionException.class,
                    () -> expiring.completion().toCompletableFuture().get(2, TimeUnit.SECONDS)).getCause());
            var cleanup = receiveMethod(peer, "SUBSCRIBE"); assertEquals("0", cleanup.headers().get("expires"));
            respond(peer, cleanup, 200, "", "remote");
            var second = server.subscribeAlarms(DEVICE, TARGET, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 1\r\n", "remote"); var stopping = second.get(2, TimeUnit.SECONDS);
            notify(peer, sub, 1, "remote", "presence", "active", ""); assertEquals(200, peer.receive().status());
            var renewal = receiveMethod(peer, "SUBSCRIBE"); var stopFuture = stopping.stop(); var stop = peer.receive();
            assertEquals("0", stop.headers().get("expires")); assertTrue(cseq(stop) > cseq(renewal));
            respond(peer, renewal, 200, "Expires: 60\r\n", "remote"); respond(peer, stop, 200, "", "remote");
            stopFuture.get(2, TimeUnit.SECONDS); assertNoPacket(peer);
        }
    }

    @Test void failsOnDeviceRebindAndServerCloseWithBestEffortUnsubscribe() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), event -> { });
             var peer = new Peer(port, "UDP"); var rebound = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeAlarms(DEVICE, TARGET, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            rebound.register(password);
            assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals("0", receiveMethod(peer, "SUBSCRIBE").headers().get("expires"));
            var another = server.subscribeAlarms(DEVICE, TARGET, request()); var next = rebound.receive();
            respond(rebound, next, 200, "Expires: 60\r\n", "remote"); var active = another.get(2, TimeUnit.SECONDS);
            server.close();
            assertThrows(ExecutionException.class, () -> active.completion().toCompletableFuture().get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.subscribeAlarms(DEVICE, TARGET, request()));
        }
    }

    @Test void rejectsMissingOrOversizedGrantedLifetimeAndUnsupportedRouteSet() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            for (String extra : List.of("", "Expires: 0\r\n", "Expires: 61\r\n",
                    "Expires: 60\r\nRecord-Route: <sip:proxy@192.0.2.1>\r\n")) {
                var opening = server.subscribeAlarms(DEVICE, TARGET, request()); var sub = receiveMethod(peer, "SUBSCRIBE");
                respond(peer, sub, 200, extra, "remote");
                assertThrows(ExecutionException.class, () -> opening.get(2, TimeUnit.SECONDS));
                var stop = receiveMethod(peer, "SUBSCRIBE"); assertEquals("0", stop.headers().get("expires"));
                respond(peer, stop, 200, "", "remote");
            }
        }
    }

    @Test void localStopSuppressesQueuedNotificationButRemoteTerminationPreservesAcceptedFinalAlarm() throws Exception {
        int port = freePort(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var delivered = new LinkedBlockingQueue<Integer>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), event -> {
            delivered.add(event.alarm().sn());
            if (event.alarm().sn() == 1) { entered.countDown(); try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
        }); var peer = new Peer(port, "UDP")) {
            peer.register(password); peer.message(alarm(DEVICE, 1)); assertEquals(200, peer.receive().status());
            peer.respond(receiveMethod(peer, "MESSAGE"), 200); assertTrue(entered.await(2, TimeUnit.SECONDS));
            var opening = server.subscribeAlarms(DEVICE, TARGET, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            notify(peer, sub, 1, "remote", "presence", "active", alarm(TARGET, 2)); assertEquals(200, peer.receive().status());
            session.stop(); var stop = peer.receive(); respond(peer, stop, 200, "", "remote");
            session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var last = server.subscribeAlarms(DEVICE, TARGET, request()); var lastSub = peer.receive();
            respond(peer, lastSub, 200, "Expires: 60\r\n", "remote"); var lastSession = last.get(2, TimeUnit.SECONDS);
            notify(peer, lastSub, 1, "remote", "presence", "terminated", alarm(TARGET, 3)); assertEquals(200, peer.receive().status());
            lastSession.completion().toCompletableFuture().get(2, TimeUnit.SECONDS); release.countDown();
            assertEquals(1, delivered.poll(2, TimeUnit.SECONDS)); assertEquals(3, delivered.poll(2, TimeUnit.SECONDS));
            assertNull(delivered.poll(100, TimeUnit.MILLISECONDS));
        } finally { release.countDown(); }
    }

    static long cseq(Wire wire) { return Long.parseLong(wire.headers().get("cseq").split(" ")[0]); }
    static String alarm(String target, int sn) {
        return "<Notify><CmdType>Alarm</CmdType><SN>" + sn + "</SN><DeviceID>" + target
                + "</DeviceID><AlarmPriority>1</AlarmPriority><AlarmMethod>2</AlarmMethod><AlarmTime>2026-09-15T12:00:00</AlarmTime></Notify>";
    }
    static void respond(Peer peer, Wire request, int status, String extra, String tag) throws Exception {
        var text = new StringBuilder("SIP/2.0 " + status + " Test\r\n");
        for (String name : List.of("via", "from", "to", "call-id", "cseq")) {
            String value = request.headers().get(name);
            if (name.equals("to") && !value.contains(";tag=")) value += ";tag=" + tag;
            text.append(name).append(": ").append(value).append("\r\n");
        }
        peer.send(text + "Contact: <sip:" + TARGET + "@192.0.2.1:5099>\r\n" + extra + "Content-Length: 0\r\n\r\n");
    }
    static void notify(Peer peer, Wire sub, long cseq, String remoteTag, String event, String state, String body) throws Exception {
        peer.send("NOTIFY sip:" + SERVER + "@" + REALM + " SIP/2.0\r\nVia: SIP/2.0/" + peer.transport
                + " 127.0.0.1:" + peer.port() + ";rport;branch=z9hG4bK" + UUID.randomUUID()
                + "\r\nFrom: <sip:" + TARGET + "@" + REALM + ">;tag=" + remoteTag + "\r\nTo: " + sub.headers().get("from")
                + "\r\nCall-ID: " + sub.headers().get("call-id") + "\r\nCSeq: " + cseq + " NOTIFY\r\nMax-Forwards: 70\r\n"
                + "Event: " + event + "\r\nSubscription-State: " + state + "\r\nContent-Type: Application/MANSCDP+xml\r\nContent-Length: "
                + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
    }
    static void assertNoPacket(Peer peer) throws Exception {
        if (peer.udp != null) peer.udp.setSoTimeout(150); else peer.tcp.setSoTimeout(150);
        assertThrows(java.net.SocketTimeoutException.class, peer::receive);
        if (peer.udp != null) peer.udp.setSoTimeout(3000); else peer.tcp.setSoTimeout(3000);
    }
}

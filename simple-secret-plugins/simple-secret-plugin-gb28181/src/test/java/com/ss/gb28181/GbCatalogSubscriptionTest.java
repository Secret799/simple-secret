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
import static com.ss.gb28181.GbAlarmSubscriptionTest.respond;
import static com.ss.gb28181.GbAlarmSubscriptionTest.cseq;
import static com.ss.gb28181.GbAlarmSubscriptionTest.assertNoPacket;
import static com.ss.gb28181.GbPlaybackTest.receiveMethod;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class GbCatalogSubscriptionTest {
    static final String TARGET = DEVICE;
    final String password = UUID.randomUUID().toString();
    static GbCatalogSubscriptionRequest request() { return GbCatalogSubscriptionRequest.all(Duration.ofSeconds(60)); }

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void subscribesReceivesOnlySipReceiptAndStopsOnRegisteredConnection(String transport) throws Exception {
        int port = freePort();
        var notifications = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, notifications::add);
             var peer = new Peer(port, transport)) {
            peer.register(password);
            var opening = server.subscribeCatalog(DEVICE, request());
            Wire subscribe = receiveMethod(peer, "SUBSCRIBE");
            assertTrue(subscribe.start().startsWith("SUBSCRIBE sip:" + TARGET + "@127.0.0.1:" + peer.port()));
            assertTrue(subscribe.headers().get("event").matches("(?i)Catalog;id=[0-9]{1,10}"));
            assertEquals("60", subscribe.headers().get("expires"));
            assertTrue(subscribe.headers().get("contact").contains(SERVER));
            assertTrue(subscribe.body().contains("<CmdType>Catalog</CmdType>"));
            respond(peer, subscribe, 200, "Expires: 30\r\n", "remote");
            GbCatalogSubscription session = opening.get(2, TimeUnit.SECONDS);
            assertEquals(DEVICE, session.deviceId());
            assertEquals(request(), session.request()); assertEquals(subscribe.headers().get("call-id"), session.callId());
            notify(peer, subscribe, 1, "remote", event(subscribe), "active;expires=30", notificationBody(TARGET, 71));
            Wire receipt = peer.receive(); assertEquals(200, receipt.status()); assertEquals("", receipt.body());
            assertEquals(71, notifications.poll(2, TimeUnit.SECONDS).notification().sn());
            assertNoPacket(peer);
            var stopping = session.stop();
            var stop = receiveMethod(peer, "SUBSCRIBE");
            assertEquals("0", stop.headers().get("expires"));
            assertEquals(subscribe.headers().get("call-id"), stop.headers().get("call-id"));
            assertTrue(cseq(stop) > cseq(subscribe)); assertEquals(event(subscribe), event(stop));
            assertTrue(stop.headers().get("to").contains(";tag=remote"));
            stopping.cancel(false);
            respond(peer, stop, 200, "", "remote");
            session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            session.stop().get(1, TimeUnit.SECONDS);
        }
    }

    @Test void acceptsEarlyNotifyWithoutCompletingOpeningAndBindsOnlyValidatedTag() throws Exception {
        int port = freePort(); var notifications = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, notifications::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            notify(peer, sub, 1, "bad", event(sub), "active", "<malformed");
            assertEquals(400, peer.receive().status());
            notify(peer, sub, 1, "remote", event(sub), "pending;expires=20", notificationBody(TARGET, 1));
            assertEquals(200, peer.receive().status()); assertNotNull(notifications.poll(2, TimeUnit.SECONDS));
            assertFalse(opening.isDone());
            respond(peer, sub, 200, "Expires: 30\r\n", "remote");
            opening.get(2, TimeUnit.SECONDS);
        }
    }

    @Test void rejectsSourceTagsEventStateMimeXmlAndStaleCseqWithoutConsumingSequence() throws Exception {
        int port = freePort(); var notifications = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, notifications::add);
             var peer = new Peer(port, "UDP"); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(spoof, sub, 403, "", "remote"); assertNoPacket(spoof);
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); opening.get(2, TimeUnit.SECONDS);
            notify(spoof, sub, 9, "remote", event(sub), "active", "<bad"); assertEquals(481, spoof.receive().status());
            notify(peer, sub, 9, "wrong", event(sub), "active", "<bad"); assertEquals(481, peer.receive().status());
            notify(peer, sub, 9, "remote", "other", "active", "<bad"); assertEquals(489, peer.receive().status());
            notify(peer, sub, 9, "remote", "presence", "active", "<bad"); assertEquals(489, peer.receive().status());
            notify(peer, sub, 9, "remote", event(sub), "active;expires=0", ""); assertEquals(400, peer.receive().status());
            notify(peer, sub, 9, "remote", event(sub), "active", notificationBody("34020000002000009999", 1)); assertEquals(400, peer.receive().status());
            notify(peer, sub, 9, "remote", event(sub), "active", notificationBody(TARGET, 2)); assertEquals(200, peer.receive().status());
            notify(peer, sub, 9, "remote", event(sub), "active", notificationBody(TARGET, 3)); assertEquals(500, peer.receive().status());
            assertEquals(2, notifications.poll(2, TimeUnit.SECONDS).notification().sn()); assertNull(notifications.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test void cancelledOpeningRetainsCapacityAndCleansLateSuccessWithoutDelivery() throws Exception {
        int port = freePort(); var notifications = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).maxCatalogSubscriptions(1).build(), id -> Optional.of(password), null, notifications::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            opening.cancel(false);
            assertThrows(IllegalStateException.class, () -> server.subscribeCatalog(DEVICE, request()));
            respond(peer, sub, 200, "Expires: 60\r\n", "remote");
            var stop = receiveMethod(peer, "SUBSCRIBE"); assertEquals("0", stop.headers().get("expires"));
            respond(peer, stop, 200, "Expires: 0\r\n", "remote");
            notify(peer, sub, 1, "remote", event(sub), "active", notificationBody(TARGET, 1)); assertEquals(200, peer.receive().status());
            assertNull(notifications.poll(150, TimeUnit.MILLISECONDS)); assertTrue(opening.isCancelled());
        }
    }

    @Test void renewsAtGrantedLifetimeWithIncreasingCseqAndFailsRejectedRenewal() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 1\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            notify(peer, sub, 1, "remote", event(sub), "active", ""); assertEquals(200, peer.receive().status());
            var renewal = receiveMethod(peer, "SUBSCRIBE"); assertEquals("60", renewal.headers().get("expires"));
            assertTrue(cseq(renewal) > cseq(sub)); assertEquals(event(sub), event(renewal)); assertTrue(renewal.headers().get("to").contains(";tag=remote"));
            respond(peer, renewal, 403, "", "remote");
            assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals("0", receiveMethod(peer, "SUBSCRIBE").headers().get("expires"));
        }
    }

    @Test void terminalNotifyCompletesStopAndCleanupAcknowledgesWithoutCallback() throws Exception {
        int port = freePort(); var notifications = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, notifications::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            var stopped = session.stop(); var stop = peer.receive(); assertEquals("0", stop.headers().get("expires"));
            notify(peer, sub, 1, "remote", event(sub), "terminated;reason=timeout", "");
            assertEquals(200, peer.receive().status()); stopped.get(2, TimeUnit.SECONDS);
            notify(peer, sub, 2, "remote", event(sub), "active", notificationBody(TARGET, 1));
            assertEquals(200, peer.receive().status());
            assertNull(notifications.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test void notificationUsesCatalogCallbackCapacityButNoMessageCapacity() throws Exception {
        int port = freePort(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var server = Gb28181Server.open(options(port).maxPendingQueries(1).maxPendingCatalogNotifications(1).build(),
                id -> Optional.of(password), null, event -> { entered.countDown(); try { release.await(3, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } });
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var query = server.queryCatalog(DEVICE); peer.respond(peer.receive(), 200);
            var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); opening.get(2, TimeUnit.SECONDS);
            notify(peer, sub, 1, "remote", event(sub), "active", notificationBody(TARGET, 1)); assertEquals(200, peer.receive().status());
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            notify(peer, sub, 2, "remote", event(sub), "active", notificationBody(TARGET, 2)); assertEquals(503, peer.receive().status());
            release.countDown(); query.cancel(false);
        } finally { release.countDown(); }
    }

    @Test void rejectsMissingListenerOfflineAndInvalidIdsSynchronously() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password)); var peer = new Peer(port, "UDP")) {
            peer.register(password); assertThrows(IllegalStateException.class, () -> server.subscribeCatalog(DEVICE, request()));
        }
        try (var server = Gb28181Server.open(options(freePort()).build(), id -> Optional.of(password), null, event -> { })) {
            assertThrows(IllegalArgumentException.class, () -> server.subscribeCatalog("bad", request()));
            assertThrows(IllegalStateException.class, () -> server.subscribeCatalog(DEVICE, request()));
        }
    }

    @Test void unansweredRenewalCannotExtendLeaseAndStopDuringRenewalCannotRevive() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).stopTimeout(Duration.ofMillis(300)).build(), id -> Optional.of(password), null, event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var first = server.subscribeCatalog(DEVICE, request()); var initial = peer.receive();
            respond(peer, initial, 200, "Expires: 1\r\n", "remote"); var expiring = first.get(2, TimeUnit.SECONDS);
            notify(peer, initial, 1, "remote", event(initial), "active", ""); assertEquals(200, peer.receive().status());
            var unanswered = receiveMethod(peer, "SUBSCRIBE"); assertEquals("60", unanswered.headers().get("expires"));
            assertInstanceOf(TimeoutException.class, assertThrows(ExecutionException.class,
                    () -> expiring.completion().toCompletableFuture().get(2, TimeUnit.SECONDS)).getCause());
            var cleanup = receiveMethod(peer, "SUBSCRIBE"); assertEquals("0", cleanup.headers().get("expires"));
            respond(peer, cleanup, 200, "", "remote");
            var second = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 1\r\n", "remote"); var stopping = second.get(2, TimeUnit.SECONDS);
            notify(peer, sub, 1, "remote", event(sub), "active", ""); assertEquals(200, peer.receive().status());
            var renewal = receiveMethod(peer, "SUBSCRIBE"); var stopFuture = stopping.stop(); var stop = peer.receive();
            assertEquals("0", stop.headers().get("expires")); assertTrue(cseq(stop) > cseq(renewal));
            respond(peer, renewal, 200, "Expires: 60\r\n", "remote"); respond(peer, stop, 200, "", "remote");
            stopFuture.get(2, TimeUnit.SECONDS); assertNoPacket(peer);
        }
    }

    @Test void failsOnDeviceRebindAndServerCloseWithBestEffortUnsubscribe() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, event -> { });
             var peer = new Peer(port, "UDP"); var rebound = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            rebound.register(password);
            assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals("0", receiveMethod(peer, "SUBSCRIBE").headers().get("expires"));
            var another = server.subscribeCatalog(DEVICE, request()); var next = rebound.receive();
            respond(rebound, next, 200, "Expires: 60\r\n", "remote"); var active = another.get(2, TimeUnit.SECONDS);
            server.close();
            assertThrows(ExecutionException.class, () -> active.completion().toCompletableFuture().get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.subscribeCatalog(DEVICE, request()));
        }
    }

    @Test void rejectsMissingOrOversizedGrantedLifetimeAndUnsupportedRouteSet() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            for (String extra : List.of("", "Expires: 0\r\n", "Expires: 61\r\n",
                    "Expires: 60\r\nRecord-Route: <sip:proxy@192.0.2.1>\r\n")) {
                var opening = server.subscribeCatalog(DEVICE, request()); var sub = receiveMethod(peer, "SUBSCRIBE");
                respond(peer, sub, 200, extra, "remote");
                assertThrows(ExecutionException.class, () -> opening.get(2, TimeUnit.SECONDS));
                var stop = receiveMethod(peer, "SUBSCRIBE"); assertEquals("0", stop.headers().get("expires"));
                respond(peer, stop, 200, "", "remote");
            }
        }
    }

    @Test void localStopSuppressesQueuedNotificationButRemoteTerminationPreservesAcceptedFinalCatalogBatch() throws Exception {
        int port = freePort(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var delivered = new LinkedBlockingQueue<Integer>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, event -> {
            delivered.add(event.notification().sn());
            if (event.notification().sn() == 1) { entered.countDown(); try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
        }); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var firstOpening = server.subscribeCatalog(DEVICE, request()); var firstSub = peer.receive();
            respond(peer, firstSub, 200, "Expires: 60\r\n", "remote"); firstOpening.get(2, TimeUnit.SECONDS);
            notify(peer, firstSub, 1, "remote", event(firstSub), "active", notificationBody(DEVICE, 1));
            assertEquals(200, peer.receive().status()); assertTrue(entered.await(2, TimeUnit.SECONDS));
            var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            notify(peer, sub, 1, "remote", event(sub), "active", notificationBody(TARGET, 2)); assertEquals(200, peer.receive().status());
            session.stop(); var stop = peer.receive(); respond(peer, stop, 200, "", "remote");
            session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var last = server.subscribeCatalog(DEVICE, request()); var lastSub = peer.receive();
            respond(peer, lastSub, 200, "Expires: 60\r\n", "remote"); var lastSession = last.get(2, TimeUnit.SECONDS);
            notify(peer, lastSub, 1, "remote", event(lastSub), "terminated", notificationBody(TARGET, 3)); assertEquals(200, peer.receive().status());
            lastSession.completion().toCompletableFuture().get(2, TimeUnit.SECONDS); release.countDown();
            assertEquals(1, delivered.poll(2, TimeUnit.SECONDS)); assertEquals(3, delivered.poll(2, TimeUnit.SECONDS));
            assertNull(delivered.poll(100, TimeUnit.MILLISECONDS));
        } finally { release.countDown(); }
    }

    @Test void routesByDialogBeforePayloadAndKeepsAlarmAndCatalogCapacityIndependent() throws Exception {
        int port = freePort(); var alarms = new LinkedBlockingQueue<GbAlarmEvent>();
        var catalogs = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).maxAlarmSubscriptions(1).maxCatalogSubscriptions(1).build(),
                id -> Optional.of(password), alarms::add, catalogs::add); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var a = server.subscribeAlarms(DEVICE, DEVICE, GbAlarmSubscriptionTest.request()); var alarmSub = peer.receive();
            var c = server.subscribeCatalog(DEVICE, request()); var catalogSub = peer.receive();
            respond(peer, alarmSub, 200, "Expires: 60\r\n", "remote"); a.get(2, TimeUnit.SECONDS);
            respond(peer, catalogSub, 200, "Expires: 60\r\n", "remote"); c.get(2, TimeUnit.SECONDS);
            notify(peer, alarmSub, 1, "remote", "presence", "active", notificationBody(DEVICE, 1));
            assertEquals(400, peer.receive().status());
            notify(peer, catalogSub, 1, "remote", event(catalogSub), "active", GbAlarmSubscriptionTest.alarm(DEVICE, 1));
            assertEquals(400, peer.receive().status());
            for (String wrong : List.of("Catalog", "presence", event(catalogSub) + "0")) {
                notify(peer, catalogSub, 1, "remote", wrong, "active", notificationBody(DEVICE, 1));
                assertEquals(489, peer.receive().status());
            }
            notify(peer, catalogSub, 1, "remote", event(catalogSub).toLowerCase(), "active", notificationBody(DEVICE, 2));
            assertEquals(200, peer.receive().status()); var delivered = catalogs.poll(2, TimeUnit.SECONDS);
            assertNotNull(delivered); assertEquals(catalogSub.headers().get("call-id"), delivered.callId());
            assertEquals(GbCatalogNotification.Type.OFF, delivered.notification().entries().get(0).type());
            assertNull(delivered.notification().entries().get(0).item().name());
            notify(peer, alarmSub, 1, "remote", "presence", "active", GbAlarmSubscriptionTest.alarm(DEVICE, 3));
            assertEquals(200, peer.receive().status()); assertNotNull(alarms.poll(2, TimeUnit.SECONDS));
            String page = "<Notify><CmdType>Catalog</CmdType><SN>4</SN><DeviceID>" + DEVICE
                    + "</DeviceID><SumNum>5</SumNum><DeviceList Num=\"1\"><Item><DeviceID>34</DeviceID><Name>Area</Name><Status>ON</Status></Item></DeviceList></Notify>";
            notify(peer, catalogSub, 2, "remote", event(catalogSub), "active", page);
            assertEquals(200, peer.receive().status()); var batch = catalogs.poll(2, TimeUnit.SECONDS).notification();
            assertEquals(5, batch.total()); assertNull(batch.entries().get(0).type());
            assertThrows(IllegalStateException.class, () -> server.subscribeCatalog(DEVICE, request()));
        }
    }

    @Test void earlyTerminalNotifyFailsOpeningButPreservesFinalPayload() throws Exception {
        int port = freePort(); var delivered = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, delivered::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            notify(peer, sub, 1, "remote", event(sub), "terminated", notificationBody(DEVICE, 7));
            assertEquals(200, peer.receive().status());
            assertThrows(ExecutionException.class, () -> opening.get(2, TimeUnit.SECONDS));
            assertEquals(7, delivered.poll(2, TimeUnit.SECONDS).notification().sn());
        }
    }

    static String event(Wire wire) { return wire.headers().get("event"); }
    static String notificationBody(String target, int sn) {
        return "<Notify><CmdType>Catalog</CmdType><SN>" + sn + "</SN><DeviceID>" + target
                + "</DeviceID><SumNum>1</SumNum><DeviceList Num=\"1\"><Item><DeviceID>34020000001320000001</DeviceID><Event>OFF</Event></Item></DeviceList></Notify>";
    }
    static void notify(Peer peer, Wire sub, long cseq, String remoteTag, String event, String state, String body) throws Exception {
        peer.send("NOTIFY sip:" + SERVER + "@" + REALM + " SIP/2.0\r\nVia: SIP/2.0/" + peer.transport
                + " 127.0.0.1:" + peer.port() + ";rport;branch=z9hG4bK" + UUID.randomUUID()
                + "\r\nFrom: <sip:" + TARGET + "@" + REALM + ">;tag=" + remoteTag + "\r\nTo: " + sub.headers().get("from")
                + "\r\nCall-ID: " + sub.headers().get("call-id") + "\r\nCSeq: " + cseq + " NOTIFY\r\nMax-Forwards: 70\r\n"
                + "Event: " + event + "\r\nSubscription-State: " + state + "\r\nContent-Type: Application/MANSCDP+xml\r\nContent-Length: "
                + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
    }
}

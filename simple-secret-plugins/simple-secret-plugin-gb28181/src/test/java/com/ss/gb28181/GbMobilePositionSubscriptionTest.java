package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.ss.gb28181.Gb28181ServerTest.*;
import static com.ss.gb28181.GbAlarmSubscriptionTest.assertNoPacket;
import static com.ss.gb28181.GbAlarmSubscriptionTest.respond;
import static com.ss.gb28181.GbPlaybackTest.receiveMethod;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class GbMobilePositionSubscriptionTest {
    private final String password = UUID.randomUUID().toString();

    private static GbMobilePositionSubscriptionRequest request() {
        return new GbMobilePositionSubscriptionRequest(Duration.ofSeconds(60), Duration.ofSeconds(7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UDP", "TCP"})
    void subscribesAndReceivesMobilePositionsOnRegisteredConnection(String transport) throws Exception {
        int port = freePort();
        var delivered = new LinkedBlockingQueue<GbMobilePositionEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, null,
                delivered::add); var peer = new Peer(port, transport)) {
            peer.register(password);
            var opening = server.subscribeMobilePosition(DEVICE, request());
            Wire subscribe = receiveMethod(peer, "SUBSCRIBE");
            assertEquals("presence", subscribe.headers().get("event"));
            assertEquals("60", subscribe.headers().get("expires"));
            assertTrue(subscribe.body().contains("<CmdType>MobilePosition</CmdType>"));
            assertTrue(subscribe.body().contains("<Interval>7</Interval>"));
            respond(peer, subscribe, 200, "Expires: 30\r\n", "remote");
            GbMobilePositionSubscription subscription = opening.get(2, TimeUnit.SECONDS);
            assertEquals(DEVICE, subscription.deviceId());
            assertEquals(subscribe.headers().get("call-id"), subscription.callId());
            assertEquals(request(), subscription.request());

            mobileNotify(peer, subscribe, 1, "remote", "presence", "active;expires=30", body(DEVICE, 71));
            assertEquals(200, peer.receive().status());
            GbMobilePositionEvent event = delivered.poll(2, TimeUnit.SECONDS);
            assertNotNull(event);
            assertEquals(DEVICE, event.sourceDeviceId());
            assertEquals(subscription.callId(), event.callId());
            assertEquals(71, event.notification().sn());
            assertEquals(121.5, event.notification().positions().get(0).longitude());
            assertNoPacket(peer);
        }
    }

    @Test
    void acceptsValidEarlyNotifyButRejectsBadPayloadWithoutBindingTagOrCseq() throws Exception {
        int port = freePort();
        var delivered = new LinkedBlockingQueue<GbMobilePositionEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, null,
                delivered::add); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.subscribeMobilePosition(DEVICE, request());
            Wire sub = peer.receive();
            mobileNotify(peer, sub, 1, "bad", "presence", "active", "<malformed");
            assertEquals(400, peer.receive().status());
            mobileNotify(peer, sub, 1, "remote", "presence", "pending;expires=20", body(DEVICE, 1));
            assertEquals(200, peer.receive().status());
            assertNotNull(delivered.poll(2, TimeUnit.SECONDS));
            assertFalse(opening.isDone());
            respond(peer, sub, 200, "Expires: 30\r\n", "remote");
            opening.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void routesSharedPresenceDialogsBeforeDecodingAndKeepsCallbacksIndependent() throws Exception {
        int port = freePort();
        var alarms = new LinkedBlockingQueue<GbAlarmEvent>();
        var positions = new LinkedBlockingQueue<GbMobilePositionEvent>();
        var alarmEntered = new CountDownLatch(1);
        var releaseAlarm = new CountDownLatch(1);
        try (var server = Gb28181Server.open(options(port).maxPendingAlarms(1)
                .maxPendingMobilePositionNotifications(1).build(), id -> Optional.of(password), event -> {
                    alarms.add(event); alarmEntered.countDown();
                    try { releaseAlarm.await(3, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }, null, positions::add); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var alarmOpening = server.subscribeAlarms(DEVICE, DEVICE, GbAlarmSubscriptionTest.request());
            Wire alarmSub = peer.receive();
            var positionOpening = server.subscribeMobilePosition(DEVICE, request());
            Wire positionSub = peer.receive();
            respond(peer, alarmSub, 200, "Expires: 60\r\n", "alarm-tag");
            alarmOpening.get(2, TimeUnit.SECONDS);
            respond(peer, positionSub, 200, "Expires: 60\r\n", "position-tag");
            positionOpening.get(2, TimeUnit.SECONDS);

            GbCatalogSubscriptionTest.notify(peer, alarmSub, 1, "alarm-tag", "presence", "active",
                    GbAlarmSubscriptionTest.alarm(DEVICE, 3));
            assertEquals(200, peer.receive().status());
            assertTrue(alarmEntered.await(2, TimeUnit.SECONDS));
            mobileNotify(peer, positionSub, 1, "position-tag", "presence", "active", body(DEVICE, 4));
            assertEquals(200, peer.receive().status());
            assertNotNull(positions.poll(2, TimeUnit.SECONDS));

            mobileNotify(peer, alarmSub, 2, "alarm-tag", "presence", "active", body(DEVICE, 5));
            assertEquals(400, peer.receive().status());
            GbCatalogSubscriptionTest.notify(peer, positionSub, 2, "position-tag", "presence", "active",
                    GbAlarmSubscriptionTest.alarm(DEVICE, 6));
            assertEquals(400, peer.receive().status());
            assertNotEquals(alarmSub.headers().get("call-id"), positionSub.headers().get("call-id"));
        } finally {
            releaseAlarm.countDown();
        }
    }

    @Test
    void rejectsUnknownDialogEventMimeOwnerMalformedStaleAndOversizedBatch() throws Exception {
        int port = freePort();
        var delivered = new LinkedBlockingQueue<GbMobilePositionEvent>();
        try (var server = Gb28181Server.open(options(port).maxMobilePositionItems(1).build(),
                id -> Optional.of(password), null, null, delivered::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.subscribeMobilePosition(DEVICE, request()); Wire sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); opening.get(2, TimeUnit.SECONDS);
            Wire unknown = new Wire(sub.start(), new java.util.HashMap<>(sub.headers()), sub.body());
            unknown.headers().put("call-id", "unknown");
            mobileNotify(peer, unknown, 1, "remote", "presence", "active", body(DEVICE, 1));
            assertEquals(481, peer.receive().status());
            mobileNotify(peer, sub, 1, "remote", "Catalog", "active", body(DEVICE, 2));
            assertEquals(489, peer.receive().status());
            sendNotify(peer, sub, 1, "remote", "presence", "active", "text/plain", body(DEVICE, 3), DEVICE);
            assertEquals(415, peer.receive().status());
            mobileNotify(peer, sub, 1, "remote", "presence", "active", body("34020000002000009999", 4));
            assertEquals(400, peer.receive().status());
            mobileNotify(peer, sub, 1, "remote", "presence", "active", twoItemsBody(DEVICE, 5));
            assertEquals(400, peer.receive().status());
            mobileNotify(peer, sub, 1, "remote", "presence", "active", body(DEVICE, 6));
            assertEquals(200, peer.receive().status());
            mobileNotify(peer, sub, 1, "remote", "presence", "active", body(DEVICE, 7));
            assertEquals(500, peer.receive().status());
            assertEquals(6, delivered.poll(2, TimeUnit.SECONDS).notification().sn());
            assertNull(delivered.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void fullPositionQueueReturns503WithoutConsumingCseqAndDoesNotUseCatalogQueue() throws Exception {
        int port = freePort();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var delivered = new LinkedBlockingQueue<Integer>();
        var catalogs = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).maxPendingMobilePositionNotifications(1)
                .maxPendingCatalogNotifications(1).build(), id -> Optional.of(password), null, catalogs::add, event -> {
                    delivered.add(event.notification().sn()); entered.countDown();
                    try { release.await(3, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.subscribeMobilePosition(DEVICE, request()); Wire sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); opening.get(2, TimeUnit.SECONDS);
            mobileNotify(peer, sub, 1, "remote", "presence", "active", body(DEVICE, 1));
            assertEquals(200, peer.receive().status()); assertTrue(entered.await(2, TimeUnit.SECONDS));
            var catalogOpening = server.subscribeCatalog(DEVICE, GbCatalogSubscriptionTest.request());
            Wire catalogSub = peer.receive();
            respond(peer, catalogSub, 200, "Expires: 60\r\n", "catalog");
            catalogOpening.get(2, TimeUnit.SECONDS);
            GbCatalogSubscriptionTest.notify(peer, catalogSub, 1, "catalog",
                    GbCatalogSubscriptionTest.event(catalogSub), "active",
                    GbCatalogSubscriptionTest.notificationBody(DEVICE, 9));
            assertEquals(200, peer.receive().status());
            assertNotNull(catalogs.poll(2, TimeUnit.SECONDS));
            mobileNotify(peer, sub, 2, "remote", "presence", "active", body(DEVICE, 2));
            assertEquals(503, peer.receive().status());
            release.countDown();
            await(() -> pendingPositions(server) == 0);
            mobileNotify(peer, sub, 2, "remote", "presence", "active", body(DEVICE, 3));
            assertEquals(200, peer.receive().status());
            assertEquals(1, delivered.poll(2, TimeUnit.SECONDS));
            assertEquals(3, delivered.poll(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test
    void rejectsMissingListenerInvalidOfflineOwnerAndSubscriptionOverflow() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password));
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            assertThrows(IllegalStateException.class, () -> server.subscribeMobilePosition(DEVICE, request()));
        }
        try (var server = Gb28181Server.open(options(port).maxMobilePositionSubscriptions(1).build(),
                id -> Optional.of(password), null, null, event -> { }); var peer = new Peer(port, "UDP")) {
            assertThrows(IllegalArgumentException.class, () -> server.subscribeMobilePosition("bad", request()));
            assertThrows(IllegalStateException.class, () -> server.subscribeMobilePosition(DEVICE, request()));
            peer.register(password);
            var first = server.subscribeMobilePosition(DEVICE, request()); peer.receive();
            assertThrows(IllegalStateException.class, () -> server.subscribeMobilePosition(DEVICE, request()));
            first.cancel(false);
        }
    }

    static void mobileNotify(Peer peer, Wire sub, long cseq, String remoteTag, String event, String state,
                             String body) throws Exception {
        sendNotify(peer, sub, cseq, remoteTag, event, state, "Application/MANSCDP+xml", body, DEVICE);
    }

    private static void sendNotify(Peer peer, Wire sub, long cseq, String remoteTag, String event, String state,
                                   String mime, String body, String from) throws Exception {
        peer.send("NOTIFY sip:" + SERVER + "@" + REALM + " SIP/2.0\r\nVia: SIP/2.0/" + peer.transport
                + " 127.0.0.1:" + peer.port() + ";rport;branch=z9hG4bK" + UUID.randomUUID()
                + "\r\nFrom: <sip:" + from + "@" + REALM + ">;tag=" + remoteTag
                + "\r\nTo: " + sub.headers().get("from") + "\r\nCall-ID: " + sub.headers().get("call-id")
                + "\r\nCSeq: " + cseq + " NOTIFY\r\nMax-Forwards: 70\r\nEvent: " + event
                + "\r\nSubscription-State: " + state + "\r\nContent-Type: " + mime + "\r\nContent-Length: "
                + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
    }

    static String body(String owner, int sn) {
        return "<Notify><CmdType>MobilePosition</CmdType><SN>" + sn + "</SN><DeviceID>" + owner
                + "</DeviceID><Time>2026-09-15T10:00:00+08:00</Time><SumNum>1</SumNum>"
                + "<DeviceList Num=\"1\"><Item><DeviceID>34020000001320000001</DeviceID>"
                + "<CaptureTime>2026-09-15T09:59:59+08:00</CaptureTime><Longitude>121.5</Longitude>"
                + "<Latitude>31.2</Latitude><Speed>3.5</Speed></Item></DeviceList></Notify>";
    }

    private static String twoItemsBody(String owner, int sn) {
        String item = "<Item><DeviceID>34020000001320000001</DeviceID><CaptureTime>2026-09-15T09:59:59Z</CaptureTime>"
                + "<Longitude>121</Longitude><Latitude>31</Latitude></Item>";
        return "<Notify><CmdType>MobilePosition</CmdType><SN>" + sn + "</SN><DeviceID>" + owner
                + "</DeviceID><Time>2026-09-15T10:00:00Z</Time><SumNum>2</SumNum><DeviceList Num=\"2\">"
                + item + item + "</DeviceList></Notify>";
    }

    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static int pendingPositions(Gb28181Server server) {
        synchronized (server) { return (Integer) field(server, "pendingMobilePositionNotifications"); }
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) { if (condition.getAsBoolean()) return; Thread.sleep(5); }
        fail("condition not satisfied before timeout");
    }
}

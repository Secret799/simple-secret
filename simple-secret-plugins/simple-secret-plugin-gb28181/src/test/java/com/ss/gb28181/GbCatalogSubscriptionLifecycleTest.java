package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import javax.sip.ClientTransaction;
import javax.sip.TimeoutEvent;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static com.ss.gb28181.Gb28181ServerTest.*;
import static com.ss.gb28181.GbAlarmSubscriptionTest.respond;
import static com.ss.gb28181.GbAlarmSubscriptionTest.cseq;
import static com.ss.gb28181.GbAlarmSubscriptionTest.assertNoPacket;
import static com.ss.gb28181.GbCatalogSubscriptionTest.*;
import static com.ss.gb28181.GbPlaybackTest.receiveMethod;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class GbCatalogSubscriptionLifecycleTest {
    final String password = UUID.randomUUID().toString();

    @Test void rejectsUnknownDialogWrongLocalTagAndWrongMimeBeforeAcceptingSameSequence() throws Exception {
        int port = freePort(); var delivered = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, delivered::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); opening.get(2, TimeUnit.SECONDS);
            var wrong = new HashMap<>(sub.headers()); wrong.put("call-id", "unknown@test");
            GbCatalogSubscriptionTest.notify(peer, new Wire(sub.start(), wrong, sub.body()), 1, "remote", event(sub), "active", "<bad");
            assertEquals(481, peer.receive().status());
            wrong = new HashMap<>(sub.headers()); wrong.put("from", sub.headers().get("from").replaceAll("tag=[^; >]+", "tag=wrong"));
            GbCatalogSubscriptionTest.notify(peer, new Wire(sub.start(), wrong, sub.body()), 1, "remote", event(sub), "active", "<bad");
            assertEquals(481, peer.receive().status());
            peer.send(notification(peer, sub, 1, "text/plain", notificationBody(TARGET, 9)));
            assertEquals(415, peer.receive().status());
            peer.send(notification(peer, sub, 1, "Application/MANSCDP+xml", notificationBody(TARGET, 10)));
            assertEquals(200, peer.receive().status()); assertEquals(10, delivered.poll(2, TimeUnit.SECONDS).notification().sn());
            assertNull(delivered.poll(100, TimeUnit.MILLISECONDS));
        }
    }

    @Test void refusesFullCallbackQueueWithoutBindingEarlyTagOrAdvancingCseq() throws Exception {
        int port = freePort(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var delivered = new LinkedBlockingQueue<Integer>();
        try (var server = Gb28181Server.open(options(port).maxPendingCatalogNotifications(1).build(), id -> Optional.of(password), null, event -> {
            delivered.add(event.notification().sn());
            if (event.notification().sn() == 1) { entered.countDown(); try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } }
        }); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var first = server.subscribeCatalog(DEVICE, request()); var firstSub = peer.receive();
            respond(peer, firstSub, 200, "Expires: 60\r\n", "remote"); first.get(2, TimeUnit.SECONDS);
            GbCatalogSubscriptionTest.notify(peer, firstSub, 1, "remote", event(firstSub), "active", notificationBody(DEVICE, 1));
            assertEquals(200, peer.receive().status()); assertTrue(entered.await(2, TimeUnit.SECONDS));
            var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            GbCatalogSubscriptionTest.notify(peer, sub, 1, "wrong", event(sub), "active", notificationBody(TARGET, 2)); assertEquals(503, peer.receive().status());
            release.countDown(); await(() -> pendingCatalogNotifications(server) == 0);
            GbCatalogSubscriptionTest.notify(peer, sub, 1, "remote", event(sub), "active", notificationBody(TARGET, 3)); assertEquals(200, peer.receive().status());
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); opening.get(2, TimeUnit.SECONDS);
            assertEquals(1, delivered.poll(2, TimeUnit.SECONDS)); assertEquals(3, delivered.poll(2, TimeUnit.SECONDS));
            assertNull(delivered.poll(100, TimeUnit.MILLISECONDS));
        } finally { release.countDown(); }
    }

    @Test void missingInitialNotifyFailsAtTimerNAndCleanupExpiryReleasesCapacity() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).maxCatalogSubscriptions(1).build(), id -> Optional.of(password), null, event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            // Advance the specific monotonic deadline, retaining real maintenance/network behavior without a 32s sleep.
            synchronized (server) { setDeadline(server, sub, "initialNotifyDeadline"); maintain(server); }
            assertInstanceOf(TimeoutException.class, assertThrows(ExecutionException.class,
                    () -> session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS)).getCause());
            var stop = receiveMethod(peer, "SUBSCRIBE"); assertEquals("0", stop.headers().get("expires"));
            respond(peer, stop, 200, "", "remote");
            assertThrows(IllegalStateException.class, () -> server.subscribeCatalog(DEVICE, request()));
            synchronized (server) { setDeadline(server, sub, "cleanupAt"); maintain(server); }
            var replacement = server.subscribeCatalog(DEVICE, request()); var fresh = peer.receive();
            assertEquals("60", fresh.headers().get("expires")); assertNotEquals(sub.headers().get("call-id"), fresh.headers().get("call-id"));
            replacement.cancel(false);
            GbCatalogSubscriptionTest.notify(peer, sub, 1, "remote", event(sub), "active", ""); assertEquals(481, peer.receive().status());
        }
    }

    @Test void oldTransactionTimeoutCannotFailAcceptedRenewal() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            ClientTransaction initial; synchronized (server) { initial = transaction(server, sub, "initial"); }
            respond(peer, sub, 200, "Expires: 1\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            GbCatalogSubscriptionTest.notify(peer, sub, 1, "remote", event(sub), "active", ""); assertEquals(200, peer.receive().status());
            var renewal = receiveMethod(peer, "SUBSCRIBE"); ClientTransaction oldRenewal;
            synchronized (server) { oldRenewal = transaction(server, sub, "renewal"); }
            respond(peer, renewal, 200, "Expires: 60\r\n", "remote");
            await(() -> field(subscription(server, sub), "renewal") == null);
            server.processTimeout(new TimeoutEvent(server, initial, javax.sip.Timeout.TRANSACTION));
            server.processTimeout(new TimeoutEvent(server, oldRenewal, javax.sip.Timeout.TRANSACTION));
            assertFalse(session.completion().toCompletableFuture().isDone());
            GbCatalogSubscriptionTest.notify(peer, sub, 2, "remote", event(sub), "active", ""); assertEquals(200, peer.receive().status());
            var stopping = session.stop(); var stop = peer.receive(); respond(peer, stop, 200, "", "remote"); stopping.get(2, TimeUnit.SECONDS);
        }
    }

    @Test void catalogCallbackCanCloseServer() throws Exception {
        int port = freePort(); var delivered = new CountDownLatch(1); Gb28181Server[] holder = new Gb28181Server[1];
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, event -> {
            assertEquals(TARGET, event.notification().deviceId()); holder[0].close(); delivered.countDown();
        }); var peer = new Peer(port, "UDP")) {
            holder[0] = server; peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            String wire = notification(peer, sub, 1, "Application/MANSCDP+xml", notificationBody(TARGET, 1))
                    .replace("From: <sip:" + TARGET, "From: <sip:" + DEVICE);
            peer.send(wire); assertEquals(200, peer.receive().status()); assertTrue(delivered.await(2, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> session.completion().toCompletableFuture().get(1, TimeUnit.SECONDS));
        }
    }

    @Test void rebindContinuationCannotCreateSubscriptionAgainstRetiredBinding() throws Exception {
        int port = freePort(); var continuation = new CompletableFuture<Boolean>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, event -> { });
             var peer = new Peer(port, "UDP"); var rebound = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            session.completion().whenComplete((unused, failure) -> {
                try { server.subscribeCatalog(DEVICE, request()); continuation.complete(true); }
                catch (IllegalStateException offline) { continuation.complete(false); }
            });
            rebound.register(password);
            assertFalse(continuation.get(2, TimeUnit.SECONDS), "old endpoint must be unavailable before completion callbacks");
            var fresh = server.subscribeCatalog(DEVICE, request()); var next = rebound.receive();
            assertTrue(next.start().contains(":" + rebound.port())); fresh.cancel(false);
        }
    }

    @Test void rebindContinuationClosingServerCannotRestoreBindings() throws Exception {
        int port = freePort(); var closed = new CountDownLatch(1);
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, event -> { });
             var peer = new Peer(port, "UDP"); var rebound = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            String authorization = rebound.authorization(rebound.challenge(), password);
            session.completion().whenComplete((unused, failure) -> { server.close(); closed.countDown(); });
            rebound.sendRegister(authorization, 3600);
            assertTrue(closed.await(3, TimeUnit.SECONDS));
            assertTrue(server.devices().isEmpty(), "registration must not be published after reentrant close");
        }
    }

    @Test void terminatedStateIgnoresExpiresZeroAndDeliversFinalCatalogBatch() throws Exception {
        int port = freePort(); var delivered = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, delivered::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            GbCatalogSubscriptionTest.notify(peer, sub, 1, "remote", event(sub), "terminated;expires=0", notificationBody(TARGET, 1));
            assertEquals(200, peer.receive().status()); session.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertNotNull(delivered.poll(2, TimeUnit.SECONDS));
        }
    }

    @Test void terminalCompletionCanCloseWithoutLeavingQueuedPayloadOrEligibility() throws Exception {
        int port = freePort(); var delivered = new LinkedBlockingQueue<GbCatalogEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, delivered::add);
             var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); var session = opening.get(2, TimeUnit.SECONDS);
            var completed = new CountDownLatch(1);
            session.completion().whenComplete((unused, failure) -> { server.close(); completed.countDown(); });
            GbCatalogSubscriptionTest.notify(peer, sub, 1, "remote", event(sub), "terminated", notificationBody(DEVICE, 1));
            assertEquals(200, peer.receive().status()); assertTrue(completed.await(2, TimeUnit.SECONDS));
            assertNull(delivered.poll(100, TimeUnit.MILLISECONDS));
            synchronized (server) {
                assertEquals(0, pendingCatalogNotifications(server));
                assertTrue(((Map<?, ?>) field(server, "catalogEligibility")).isEmpty());
                assertTrue(((Map<?, ?>) field(field(server, "catalogSubscriptions"), "subscriptions")).isEmpty());
            }
        }
    }

    @Test void catalogListenerFailureDoesNotBlockLaterNotificationsOrAlarmQueue() throws Exception {
        int port = freePort(); var attempts = new LinkedBlockingQueue<Integer>(); var alarms = new LinkedBlockingQueue<GbAlarmEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), alarms::add, event -> {
            attempts.add(event.notification().sn()); if (event.notification().sn() == 1) throw new IllegalStateException("listener failure");
        }); var peer = new Peer(port, "UDP")) {
            peer.register(password); var opening = server.subscribeCatalog(DEVICE, request()); var sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote"); opening.get(2, TimeUnit.SECONDS);
            for (int sequence = 1; sequence <= 2; sequence++) {
                GbCatalogSubscriptionTest.notify(peer, sub, sequence, "remote", event(sub), "active", notificationBody(DEVICE, sequence));
                assertEquals(200, peer.receive().status()); assertEquals(sequence, attempts.poll(2, TimeUnit.SECONDS));
            }
            peer.message(GbAlarmSubscriptionTest.alarm(DEVICE, 3)); assertEquals(200, peer.receive().status());
            peer.respond(receiveMethod(peer, "MESSAGE"), 200); assertNotNull(alarms.poll(2, TimeUnit.SECONDS));
        }
    }

    static String notification(Peer peer, Wire sub, long cseq, String mime, String body) {
        return "NOTIFY sip:" + SERVER + "@" + REALM + " SIP/2.0\r\nVia: SIP/2.0/" + peer.transport
                + " 127.0.0.1:" + peer.port() + ";rport;branch=z9hG4bK" + UUID.randomUUID()
                + "\r\nFrom: <sip:" + TARGET + "@" + REALM + ">;tag=remote\r\nTo: " + sub.headers().get("from")
                + "\r\nCall-ID: " + sub.headers().get("call-id") + "\r\nCSeq: " + cseq + " NOTIFY\r\nMax-Forwards: 70\r\n"
                + "Event: " + event(sub) + "\r\nSubscription-State: active\r\nContent-Type: " + mime + "\r\nContent-Length: "
                + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body;
    }
    static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    static Object subscription(Gb28181Server server, Wire sub) {
        synchronized (server) {
            Object manager = field(server, "catalogSubscriptions");
            return ((Map<?, ?>) field(manager, "subscriptions")).get(sub.headers().get("call-id"));
        }
    }
    static void setDeadline(Gb28181Server server, Wire sub, String name) throws Exception {
        var entry = subscription(server, sub); var deadline = entry.getClass().getDeclaredField(name);
        deadline.setAccessible(true); deadline.setLong(entry, System.nanoTime() - 1);
    }
    static ClientTransaction transaction(Gb28181Server server, Wire sub, String name) {
        return (ClientTransaction) field(field(subscription(server, sub), name), "transaction");
    }
    static int pendingCatalogNotifications(Gb28181Server server) { synchronized (server) { return (Integer) field(server, "pendingCatalogNotifications"); } }
    static void maintain(Gb28181Server server) throws Exception {
        Object manager = field(server, "catalogSubscriptions"); var method = manager.getClass().getDeclaredMethod("maintain");
        method.setAccessible(true); method.invoke(manager);
    }
    static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) { if (condition.getAsBoolean()) return; Thread.sleep(5); }
        fail("condition not satisfied before timeout");
    }
}

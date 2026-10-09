package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.ss.gb28181.Gb28181ServerTest.*;
import static com.ss.gb28181.GbAlarmSubscriptionTest.respond;
import static com.ss.gb28181.GbPlaybackTest.receiveMethod;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class GbMobilePositionSubscriptionLifecycleTest {
    private final String password = UUID.randomUUID().toString();

    private static GbMobilePositionSubscriptionRequest request() {
        return new GbMobilePositionSubscriptionRequest(Duration.ofSeconds(60));
    }

    @Test
    void stopRebindAndCloseRouteThroughMobilePositionManager() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, null,
                event -> { }); var peer = new Peer(port, "UDP"); var rebound = new Peer(port, "UDP")) {
            peer.register(password);
            var first = server.subscribeMobilePosition(DEVICE, request()); Wire sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote");
            var subscription = first.get(2, TimeUnit.SECONDS);
            GbMobilePositionSubscriptionTest.mobileNotify(peer, sub, 1, "remote", "presence", "active", "");
            assertEquals(200, peer.receive().status());
            var stopped = subscription.stop(); Wire stop = receiveMethod(peer, "SUBSCRIBE");
            assertEquals("0", stop.headers().get("expires")); respond(peer, stop, 200, "Expires: 0\r\n", "remote");
            stopped.get(2, TimeUnit.SECONDS);

            var next = server.subscribeMobilePosition(DEVICE, request()); Wire nextSub = peer.receive();
            respond(peer, nextSub, 200, "Expires: 60\r\n", "next"); var active = next.get(2, TimeUnit.SECONDS);
            rebound.register(password);
            assertThrows(ExecutionException.class,
                    () -> active.completion().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals("0", receiveMethod(peer, "SUBSCRIBE").headers().get("expires"));

            var last = server.subscribeMobilePosition(DEVICE, request()); Wire lastSub = rebound.receive();
            respond(rebound, lastSub, 200, "Expires: 60\r\n", "last"); var lastSession = last.get(2, TimeUnit.SECONDS);
            server.close();
            assertThrows(ExecutionException.class,
                    () -> lastSession.completion().toCompletableFuture().get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.subscribeMobilePosition(DEVICE, request()));
        }
    }

    @Test
    void localCancellationSuppressesQueuedPayloadButRemoteTerminationDeliversFinalPayload() throws Exception {
        int port = freePort();
        var delivered = new LinkedBlockingQueue<Integer>();
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), null, null, event -> {
                    delivered.add(event.notification().sn());
                    if (event.notification().sn() == 1) try { release.await(3, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var blocker = server.subscribeMobilePosition(DEVICE, request()); Wire blockerSub = peer.receive();
            respond(peer, blockerSub, 200, "Expires: 60\r\n", "one"); blocker.get(2, TimeUnit.SECONDS);
            GbMobilePositionSubscriptionTest.mobileNotify(peer, blockerSub, 1, "one", "presence", "active",
                    GbMobilePositionSubscriptionTest.body(DEVICE, 1));
            assertEquals(200, peer.receive().status());

            var opening = server.subscribeMobilePosition(DEVICE, request()); Wire sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "two"); var local = opening.get(2, TimeUnit.SECONDS);
            GbMobilePositionSubscriptionTest.mobileNotify(peer, sub, 1, "two", "presence", "active",
                    GbMobilePositionSubscriptionTest.body(DEVICE, 2));
            assertEquals(200, peer.receive().status());
            local.stop(); Wire stop = receiveMethod(peer, "SUBSCRIBE"); respond(peer, stop, 200, "", "two");

            var terminalOpening = server.subscribeMobilePosition(DEVICE, request()); Wire terminalSub = peer.receive();
            respond(peer, terminalSub, 200, "Expires: 60\r\n", "three");
            var terminal = terminalOpening.get(2, TimeUnit.SECONDS);
            GbMobilePositionSubscriptionTest.mobileNotify(peer, terminalSub, 1, "three", "presence", "terminated",
                    GbMobilePositionSubscriptionTest.body(DEVICE, 3));
            assertEquals(200, peer.receive().status());
            terminal.completion().toCompletableFuture().get(2, TimeUnit.SECONDS);
            release.countDown();
            assertEquals(1, delivered.poll(2, TimeUnit.SECONDS));
            assertEquals(3, delivered.poll(2, TimeUnit.SECONDS));
            assertNull(delivered.poll(100, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test
    void maintenanceFailsMissingInitialNotifyAndLateAcceptanceOfCancelledOpeningIsStopped() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).maxMobilePositionSubscriptions(2).build(),
                id -> Optional.of(password), null, null, event -> { }); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var opening = server.subscribeMobilePosition(DEVICE, request()); Wire sub = peer.receive();
            respond(peer, sub, 200, "Expires: 60\r\n", "remote");
            var active = opening.get(2, TimeUnit.SECONDS);
            synchronized (server) {
                Object manager = field(server, "mobilePositionSubscriptions");
                Object entry = ((java.util.Map<?, ?>) field(manager, "subscriptions"))
                        .get(sub.headers().get("call-id"));
                var deadline = entry.getClass().getDeclaredField("initialNotifyDeadline");
                deadline.setAccessible(true); deadline.setLong(entry, System.nanoTime() - 1);
                var maintain = Gb28181Server.class.getDeclaredMethod("maintain");
                maintain.setAccessible(true); maintain.invoke(server);
            }
            assertInstanceOf(TimeoutException.class, assertThrows(ExecutionException.class,
                    () -> active.completion().toCompletableFuture().get(2, TimeUnit.SECONDS)).getCause());
            Wire cleanup = receiveMethod(peer, "SUBSCRIBE"); assertEquals("0", cleanup.headers().get("expires"));
            respond(peer, cleanup, 200, "", "remote");

            var cancelled = server.subscribeMobilePosition(DEVICE, request()); Wire pending = peer.receive();
            cancelled.cancel(false);
            respond(peer, pending, 200, "Expires: 60\r\n", "late");
            Wire lateStop = receiveMethod(peer, "SUBSCRIBE"); assertEquals("0", lateStop.headers().get("expires"));
            respond(peer, lateStop, 200, "Expires: 0\r\n", "late");
            assertTrue(cancelled.isCancelled());
        }
    }

    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}

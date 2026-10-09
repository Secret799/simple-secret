package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.SocketTimeoutException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.sip.SipProvider;
import javax.sip.TransactionUnavailableException;

import static com.ss.gb28181.Gb28181ServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GbAlarmServerTest {
    private final String password = UUID.randomUUID().toString();

    @Test
    void threeArgumentOpenRequiresListener() {
        assertThrows(NullPointerException.class, () -> Gb28181Server.open(options(5060).build(),
                id -> Optional.of(password), null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UDP", "TCP"})
    void acknowledgesAlarmThenSendsIndependentResponseToRegisteredOwner(String transport) throws Exception {
        int port = freePort();
        var events = new CopyOnWriteArrayList<GbAlarmEvent>();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), events::add);
             var peer = new Peer(port, transport)) {
            peer.register(password);

            peer.message(alarm("3402000000", 77));
            assertEquals(200, peer.receive().status(), "valid Alarm must receive the empty SIP receipt first");
            Wire response = peer.receive();
            assertTrue(response.start().startsWith("MESSAGE sip:" + DEVICE + "@127.0.0.1:" + peer.port()),
                    "response must target the authenticated registration owner and actual endpoint");
            assertTrue(response.body().contains("<CmdType>Alarm</CmdType>"));
            assertTrue(response.body().contains("<SN>77</SN>"), "XML response preserves the reported SN");
            assertTrue(response.body().contains("<DeviceID>3402000000</DeviceID>"));
            assertTrue(response.body().contains("<Result>OK</Result>"));
            assertNotEquals("77", match(response.headers().get("cseq"), "([0-9]+) MESSAGE"),
                    "the internal transaction sequence must not reuse the alarm XML SN");
            peer.respond(response, 200);

            await(() -> events.size() == 1);
            GbAlarmEvent event = events.get(0);
            assertEquals(DEVICE, event.sourceDeviceId());
            assertEquals("3402000000", event.alarm().deviceId());
            assertEquals(77, event.alarm().sn());
            assertNotNull(event.receivedAt());
        }
    }

    @Test
    void rejectsAlarmWithoutListenerAndRejectsSpoofBeforeParsing() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password));
             var peer = new Peer(port, "UDP"); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            peer.message(alarm(DEVICE, 1));
            assertEquals(503, peer.receive().status());
            assertNoMessage(peer);

            spoof.message("<not-even-valid-xml");
            assertEquals(403, spoof.receive().status(), "registration source must be checked before XML parsing");
        }
    }

    @Test
    void validatesAlarmXmlAndMimeAfterSourceAuthentication() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password), event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            peer.message("<not-even-valid-xml");
            assertEquals(400, peer.receive().status());
            peer.sendRequest("MESSAGE", "Content-Type: text/plain\r\n", alarm(DEVICE, 1));
            assertEquals(415, peer.receive().status());
        }
    }

    @Test
    void boundsRunningCallbacksWithoutBlockingSipAndRecoversCapacity() throws Exception {
        int port = freePort();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var delivered = new CopyOnWriteArrayList<Integer>();
        GbAlarmListener listener = event -> {
            delivered.add(event.alarm().sn());
            entered.countDown();
            if (event.alarm().sn() == 1) {
                try { release.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        };
        try (var server = Gb28181Server.open(options(port).maxPendingAlarms(1).build(),
                id -> Optional.of(password), listener); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            peer.message(alarm(DEVICE, 1));
            assertEquals(200, peer.receive().status());
            Wire firstResponse = peer.receive();
            peer.respond(firstResponse, 200);
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            peer.message(alarm(DEVICE, 2));
            assertEquals(503, peer.receive().status(), "running callbacks count against alarm capacity");
            assertNoMessage(peer);

            release.countDown();
            int acceptedSn = sendUntilAccepted(peer, 3);
            await(() -> delivered.equals(java.util.List.of(1, acceptedSn)));
        } finally {
            release.countDown();
        }
    }

    @Test
    void sharesMessageCapacityAndReleasesItAfterAlarmResponseTimeout() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).maxPendingQueries(1).maxPendingAlarms(2)
                .queryTimeout(Duration.ofMillis(300)).build(), id -> Optional.of(password), event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            peer.message(alarm(DEVICE, 8));
            assertEquals(200, peer.receive().status());
            peer.receive(); // Deliberately leave the outbound Alarm response unacknowledged.
            assertThrows(IllegalStateException.class, () -> server.queryCatalog(DEVICE));

            CompletableFuture<?>[] queryHolder = new CompletableFuture<?>[1];
            await(() -> {
                try { queryHolder[0] = server.queryCatalog(DEVICE); return true; }
                catch (IllegalStateException full) { return false; }
            });
            var query = queryHolder[0];
            assertFalse(query.isDone(), "timeout must release the shared MESSAGE reservation");
        }
    }

    @Test
    void alarmXmlSnDoesNotCorrelateOrCompleteOutstandingQuery() throws Exception {
        int port = freePort();
        var delivered = new CopyOnWriteArrayList<Integer>();
        try (var server = Gb28181Server.open(options(port).maxPendingQueries(2).build(),
                id -> Optional.of(password), event -> delivered.add(event.alarm().sn()));
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var query = server.queryCatalog(DEVICE);
            Wire catalogRequest = peer.receive();
            String sharedSn = match(catalogRequest.body(), "<SN>([0-9]+)</SN>");
            peer.respond(catalogRequest, 200);

            peer.message(alarm(DEVICE, Integer.parseInt(sharedSn)));
            assertEquals(200, peer.receive().status());
            Wire alarmResponse = peer.receive();
            assertNotEquals(sharedSn, match(alarmResponse.headers().get("cseq"), "([0-9]+) MESSAGE"));
            peer.respond(alarmResponse, 200);
            await(() -> delivered.equals(java.util.List.of(Integer.parseInt(sharedSn))));
            assertFalse(query.isDone(), "Alarm XML SN must not be used as the pending query key");

            peer.message(catalog(sharedSn, 1, "34020000001320000002", "camera"));
            assertEquals(200, peer.receive().status());
            assertEquals("camera", query.get(2, TimeUnit.SECONDS).get(0).name());
        }
    }

    @Test
    void callbackDoesNotWaitForAlarmResponseAckAndRejectionReleasesPendingCapacity() throws Exception {
        int port = freePort();
        var delivered = new CountDownLatch(1);
        try (var server = Gb28181Server.open(options(port).maxPendingQueries(1).build(),
                id -> Optional.of(password), event -> delivered.countDown()); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            peer.message(alarm(DEVICE, 20));
            assertEquals(200, peer.receive().status());
            Wire alarmResponse = peer.receive();
            assertTrue(delivered.await(2, TimeUnit.SECONDS), "callback must not wait for the device SIP ACK");
            assertThrows(IllegalStateException.class, () -> server.queryCatalog(DEVICE));

            peer.respond(alarmResponse, 403);
            CompletableFuture<?>[] queryHolder = new CompletableFuture<?>[1];
            await(() -> {
                try { queryHolder[0] = server.queryCatalog(DEVICE); return true; }
                catch (IllegalStateException full) { return false; }
            });
            assertFalse(queryHolder[0].isDone());
        }
    }

    @Test
    void synchronousAlarmResponseSendFailureStillDeliversAndReleasesPendingCapacity() throws Exception {
        int port = freePort();
        var delivered = new CountDownLatch(1);
        try (var server = Gb28181Server.open(options(port).maxPendingQueries(1).build(),
                id -> Optional.of(password), event -> delivered.countDown()); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            Map<String, SipProvider> providers = providers(server);
            SipProvider realProvider;
            synchronized (server) {
                realProvider = providers.get("UDP");
            }
            SipProvider failingProvider = (SipProvider) Proxy.newProxyInstance(SipProvider.class.getClassLoader(),
                    new Class<?>[]{SipProvider.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getNewClientTransaction"))
                            throw new TransactionUnavailableException("test send failure");
                        try { return method.invoke(realProvider, arguments); }
                        catch (InvocationTargetException invocation) { throw invocation.getCause(); }
                    });
            synchronized (server) {
                providers.put("UDP", failingProvider);
            }

            peer.message(alarm(DEVICE, 21));
            assertEquals(200, peer.receive().status());
            assertTrue(delivered.await(2, TimeUnit.SECONDS), "receipt commits accepted callback delivery");
            assertNoMessage(peer);

            synchronized (server) {
                providers.put("UDP", realProvider);
            }
            var query = server.queryCatalog(DEVICE);
            assertFalse(query.isDone(), "synchronous send failure must release pending MESSAGE capacity");
            query.cancel(false);
        }
    }

    @Test
    void rejectsAlarmWhenExistingQueryConsumesSharedMessageCapacity() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).maxPendingQueries(1).build(),
                id -> Optional.of(password), event -> { }); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var query = server.queryCatalog(DEVICE);
            Wire request = peer.receive();
            peer.respond(request, 200);
            peer.message(alarm(DEVICE, 6));
            assertEquals(503, peer.receive().status());
            assertNoMessage(peer);
            query.cancel(false);
        }
    }

    @Test
    void alarmDoesNotRefreshHeartbeat() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).heartbeatInterval(Duration.ofMillis(150))
                .heartbeatMisses(2).build(), id -> Optional.of(password), event -> { });
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            peer.message(alarm(DEVICE, 9));
            assertEquals(200, peer.receive().status());
            peer.respond(peer.receive(), 200);
            await(() -> server.devices().isEmpty());
        }
    }

    @Test
    void listenerFailureDoesNotStopLaterDeliveryAndCallbackMayCloseServer() throws Exception {
        int port = freePort();
        var delivered = new CopyOnWriteArrayList<Integer>();
        var closedFromCallback = new CountDownLatch(1);
        Gb28181Server[] holder = new Gb28181Server[1];
        GbAlarmListener listener = event -> {
            delivered.add(event.alarm().sn());
            if (event.alarm().sn() == 1) throw new IllegalStateException("business failure");
            holder[0].devices();
            holder[0].close();
            closedFromCallback.countDown();
        };
        try (var server = Gb28181Server.open(options(port).maxPendingAlarms(2).build(),
                id -> Optional.of(password), listener); var peer = new Peer(port, "UDP")) {
            holder[0] = server;
            peer.register(password);
            for (int sn : java.util.List.of(1, 2)) {
                peer.message(alarm(DEVICE, sn));
                assertEquals(200, peer.receive().status());
                peer.respond(peer.receive(), 200);
            }
            assertTrue(closedFromCallback.await(2, TimeUnit.SECONDS), "reentrant close must not deadlock");
            assertEquals(java.util.List.of(1, 2), delivered);
            assertThrows(IllegalStateException.class, () -> server.queryCatalog(DEVICE));
        }
    }

    @Test
    void closeInterruptsRunningListenerAndDiscardsQueuedAlarm() throws Exception {
        int port = freePort();
        var entered = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var delivered = new CopyOnWriteArrayList<Integer>();
        GbAlarmListener listener = event -> {
            delivered.add(event.alarm().sn());
            entered.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException expected) { interrupted.countDown(); Thread.currentThread().interrupt(); }
        };
        var server = Gb28181Server.open(options(port).maxPendingAlarms(2).build(),
                id -> Optional.of(password), listener);
        try (server; var peer = new Peer(port, "UDP")) {
            peer.register(password);
            for (int sn : java.util.List.of(10, 11)) {
                peer.message(alarm(DEVICE, sn));
                assertEquals(200, peer.receive().status());
                peer.respond(peer.receive(), 200);
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            server.close();
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertEquals(java.util.List.of(10), delivered, "queued callbacks must be discarded on close");
        }
    }

    @Test
    void listenerEnabledStartupFailureReleasesSocketAndWorkerResources() throws Exception {
        int port = freePort();
        long workersBefore = alarmWorkerCount();
        try (var occupied = new java.net.DatagramSocket(new java.net.InetSocketAddress("127.0.0.1", port))) {
            assertThrows(IllegalStateException.class, () -> Gb28181Server.open(options(port).build(),
                    id -> Optional.of(password), event -> { }));
            try (var tcp = new java.net.ServerSocket(port, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
                assertTrue(tcp.isBound(), "TCP listener created before UDP failure must be released");
            }
        }
        await(() -> alarmWorkerCount() == workersBefore);
    }

    private static String alarm(String reportedDevice, int sn) {
        return "<Notify><CmdType>Alarm</CmdType><SN>" + sn + "</SN><DeviceID>" + reportedDevice
                + "</DeviceID><AlarmPriority>1</AlarmPriority><AlarmMethod>2</AlarmMethod>"
                + "<AlarmTime>2026-09-15T12:34:56+08:00</AlarmTime><AlarmDescription>door</AlarmDescription>"
                + "<Longitude>121.5</Longitude><Latitude>31.2</Latitude>"
                + "<Info><AlarmType>3</AlarmType><AlarmTypeParam><EventType>1</EventType>"
                + "</AlarmTypeParam></Info></Notify>";
    }

    private static void assertNoMessage(Peer peer) throws Exception {
        if (peer.udp != null) peer.udp.setSoTimeout(200);
        else peer.tcp.setSoTimeout(200);
        assertThrows(SocketTimeoutException.class, peer::receive);
        if (peer.udp != null) peer.udp.setSoTimeout(3000);
        else peer.tcp.setSoTimeout(3000);
    }

    private static int sendUntilAccepted(Peer peer, int firstSn) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        int sn = firstSn;
        while (true) {
            peer.message(alarm(DEVICE, sn++));
            Wire receipt = peer.receive();
            if (receipt.status() == 200) {
                peer.respond(peer.receive(), 200);
                return sn - 1;
            }
            assertEquals(503, receipt.status());
            assertNoMessage(peer);
            if (System.nanoTime() >= deadline) fail("alarm callback capacity was not released");
        }
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        fail("condition was not satisfied before timeout");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, SipProvider> providers(Gb28181Server server) throws Exception {
        var field = Gb28181Server.class.getDeclaredField("providers");
        field.setAccessible(true);
        return (Map<String, SipProvider>) field.get(server);
    }

    private static long alarmWorkerCount() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().equals("simple-secret-gb-alarm"))
                .count();
    }
}

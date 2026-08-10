package com.ss.mqttv5.client;

import com.ss.mqttv5.config.MqttClientOptions;
import com.ss.mqttv5.handler.MqttMessageHandler;
import com.ss.mqttv5.message.MqttMessageContext;
import com.ss.mqttv5.waiter.DefaultMqttResponseWaiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttClientManagerLifecycleTest {

    @Test
    void refreshCreatesKeepsReplacesAndRemovesClients() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.manager.refreshClients(Map.of("default", options("tcp://one", true)), (key, value) -> { });
            await(() -> fixture.clients.size() == 1 && fixture.clients.get(0).connected);

            fixture.manager.refreshClients(Map.of("default", options("tcp://one", true)), (key, value) -> { });
            assertEquals(1, fixture.clients.size());

            fixture.manager.refreshClients(Map.of("default", options("tcp://two", true)), (key, value) -> { });
            await(() -> fixture.clients.size() == 2 && fixture.clients.get(1).connected);
            assertTrue(fixture.clients.get(0).closed);

            fixture.manager.refreshClients(Map.of(), (key, value) -> { });
            await(() -> fixture.clients.get(1).closed);
            assertFalse(fixture.manager.containsClient("default"));
        } finally {
            fixture.close();
        }
    }

    @Test
    void runtimeSubscriptionSurvivesFingerprintChangingRefresh() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.manager.refreshClients(Map.of("default", options("tcp://one", true)),
                    (key, value) -> { });
            await(() -> fixture.clients.size() == 1 && fixture.clients.get(0).connected);
            MqttMessageHandler runtimeHandler = handler("runtime/+", "", 1);
            fixture.manager.subscribe("default", runtimeHandler);

            fixture.manager.refreshClients(Map.of("default", options("tcp://two", true)),
                    (key, value) -> { });

            await(() -> fixture.clients.size() == 2
                    && fixture.clients.get(1).activeSubscriptions.contains("runtime/+"));
            assertEquals(List.of(runtimeHandler), fixture.manager.getContext("default")
                    .getMatchingHandlers("runtime/device"));
        } finally {
            fixture.close();
        }
    }

    @Test
    void initialConnectionRunsWhenReconnectIsDisabled() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.manager.refreshClients(Map.of("default", options("tcp://one", false)),
                    (key, value) -> { });

            await(() -> !fixture.clients.isEmpty() && fixture.clients.get(0).connectCount == 1);
            assertTrue(fixture.manager.isConnected("default"));
        } finally {
            fixture.close();
        }
    }

    @Test
    void nullAndLiteralNullCredentialsHaveDistinctFingerprints() throws Exception {
        Fixture fixture = new Fixture();
        try {
            MqttClientOptions first = options("tcp://one", true);
            first.setUsername(null);
            fixture.manager.refreshClients(Map.of("default", first), (key, value) -> { });
            await(() -> fixture.clients.size() == 1 && fixture.clients.get(0).connected);

            MqttClientOptions second = options("tcp://one", true);
            second.setUsername("null");
            fixture.manager.refreshClients(Map.of("default", second), (key, value) -> { });

            await(() -> fixture.clients.size() == 2 && fixture.clients.get(1).connected);
            assertTrue(fixture.clients.get(0).closed);
        } finally {
            fixture.close();
        }
    }

    @Test
    void repeatedDisconnectSchedulesOnlyOneReconnectAndRestoresConnectedHook() throws Exception {
        Fixture fixture = new Fixture();
        try {
            int[] connectedCount = {0};
            MqttClientOptions options = options("tcp://one", true);
            options.setReconnectDelayMillis(10);
            fixture.manager.refreshClients(Map.of("default", options),
                    (key, value) -> connectedCount[0]++);
            await(() -> !fixture.clients.isEmpty() && connectedCount[0] == 1);
            FakeMqttClientAdapter client = fixture.clients.get(0);

            client.disconnectUnexpectedly();
            client.disconnectUnexpectedly();

            await(() -> client.connectCount == 2 && connectedCount[0] == 2);
            Thread.sleep(30);
            assertEquals(2, client.connectCount);
            assertEquals(0, fixture.manager.reconnectTaskCount());
        } finally {
            fixture.close();
        }
    }

    @Test
    void failedConnectedHookIsRetriedOnce() throws Exception {
        Fixture fixture = new Fixture();
        try {
            MqttClientOptions options = options("tcp://one", true);
            options.setReconnectDelayMillis(10);
            AtomicInteger callbackCount = new AtomicInteger();

            fixture.manager.refreshClients(Map.of("default", options), (key, value) -> {
                if (callbackCount.incrementAndGet() == 1) {
                    throw new IllegalStateException("transient callback failure");
                }
            });

            await(() -> callbackCount.get() == 2);
            assertEquals(1, fixture.clients.get(0).connectCount);
        } finally {
            fixture.close();
        }
    }

    @Test
    void reconnectRestoresWireSubscriptionsForRegisteredHandlers() throws Exception {
        Fixture fixture = new Fixture();
        try {
            MqttClientOptions options = options("tcp://one", true);
            options.setReconnectDelayMillis(10);
            MqttMessageHandler handler = new MqttMessageHandler() {
                @Override
                public String topic() {
                    return "devices/+";
                }

                @Override
                public void handle(MqttMessageContext message) {
                }
            };
            fixture.manager.refreshClients(Map.of("default", options),
                    (key, value) -> fixture.manager.subscribe(key, handler));
            await(() -> !fixture.clients.isEmpty()
                    && fixture.clients.get(0).subscriptions.size() == 1);
            FakeMqttClientAdapter client = fixture.clients.get(0);

            client.disconnectUnexpectedly();

            await(() -> client.connectCount == 2 && client.subscriptions.size() == 2);
            assertEquals(2, client.subscriptions.size());
        } finally {
            fixture.close();
        }
    }

    @Test
    void reconnectRestoresEachWireFilterOnceAtMaximumQos() throws Exception {
        Fixture fixture = new Fixture();
        try {
            MqttClientOptions options = options("tcp://one", true);
            options.setReconnectDelayMillis(10);
            MqttMessageHandler highQos = handler("devices/+", "workers", 2);
            MqttMessageHandler lowQos = handler("devices/+", "workers", 0);
            fixture.manager.refreshClients(Map.of("default", options), (key, value) -> {
                fixture.manager.subscribe(key, highQos);
                fixture.manager.subscribe(key, lowQos);
            });
            await(() -> !fixture.clients.isEmpty()
                    && fixture.clients.get(0).subscriptions.size() >= 1);
            FakeMqttClientAdapter client = fixture.clients.get(0);
            assertEquals(1, client.subscriptions.size());

            client.disconnectUnexpectedly();

            await(() -> client.connectCount == 2 && client.subscriptions.size() >= 2);
            assertEquals(2, client.subscriptions.size());
            assertEquals("$share/workers/devices/+",
                    client.subscriptions.get(1).getTopic());
            assertEquals(2, client.subscriptions.get(1).getQos());
        } finally {
            fixture.close();
        }
    }

    @Test
    void reconnectRetriesTransientSubscriptionRestoreFailure() throws Exception {
        Fixture fixture = new Fixture();
        try {
            MqttClientOptions options = options("tcp://one", true);
            options.setReconnectDelayMillis(10);
            MqttMessageHandler handler = handler("devices/+", "", 1);
            fixture.manager.refreshClients(Map.of("default", options),
                    (key, value) -> fixture.manager.subscribe(key, handler));
            await(() -> !fixture.clients.isEmpty()
                    && fixture.clients.get(0).activeSubscriptions.contains("devices/+"));
            FakeMqttClientAdapter client = fixture.clients.get(0);
            client.subscribeFailuresRemaining = 1;

            client.disconnectUnexpectedly();

            await(() -> client.connectCount == 2
                    && client.activeSubscriptions.contains("devices/+"));
            assertEquals(2, client.subscriptions.size());
        } finally {
            fixture.close();
        }
    }

    @Test
    void closeAndStaleCallbackCannotReconnect() throws Exception {
        Fixture fixture = new Fixture();
        try {
            MqttClientOptions firstOptions = options("tcp://one", true);
            firstOptions.setReconnectDelayMillis(5);
            fixture.manager.refreshClients(Map.of("default", firstOptions), (key, value) -> { });
            await(() -> !fixture.clients.isEmpty() && fixture.clients.get(0).connected);
            FakeMqttClientAdapter oldClient = fixture.clients.get(0);

            fixture.manager.refreshClients(Map.of("default", options("tcp://two", true)),
                    (key, value) -> { });
            await(() -> fixture.clients.size() == 2 && fixture.clients.get(1).connected);
            oldClient.disconnectUnexpectedly();
            Thread.sleep(20);
            assertEquals(1, oldClient.connectCount);

            FakeMqttClientAdapter currentClient = fixture.clients.get(1);
            fixture.manager.close("default");
            currentClient.disconnectUnexpectedly();
            Thread.sleep(20);
            assertEquals(1, currentClient.connectCount);
            assertEquals(0, fixture.manager.reconnectTaskCount());
        } finally {
            fixture.close();
        }
    }

    @Test
    void closeDuringConnectCleansClientAfterConnectReturns() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch connectStarted = new CountDownLatch(1);
        CountDownLatch allowConnect = new CountDownLatch(1);
        CountDownLatch connectFinished = new CountDownLatch(1);
        fixture.clientInitializer = client -> {
            client.connectHook = () -> {
                connectStarted.countDown();
                awaitUnchecked(allowConnect);
            };
            client.connectFinishedHook = connectFinished::countDown;
        };
        try {
            fixture.manager.refreshClients(Map.of("default", options("tcp://one", true)),
                    (key, value) -> { });
            assertTrue(connectStarted.await(1, TimeUnit.SECONDS));

            fixture.manager.close("default");
            allowConnect.countDown();

            assertTrue(connectFinished.await(1, TimeUnit.SECONDS));
            await(() -> !fixture.clients.get(0).connected);
            assertTrue(fixture.clients.get(0).closed);
            assertFalse(fixture.manager.containsClient("default"));
        } finally {
            allowConnect.countDown();
            fixture.close();
        }
    }

    private MqttClientOptions options(String broker, boolean reconnect) {
        MqttClientOptions options = new MqttClientOptions();
        options.setEnabled(true);
        options.setBroker(broker);
        options.setClientId("client-1");
        options.setReconnectEnabled(reconnect);
        return options;
    }

    private MqttMessageHandler handler(String filter, String shareGroup, int qos) {
        return new MqttMessageHandler() {
            @Override
            public String topic() {
                return filter;
            }

            @Override
            public String shareGroup() {
                return shareGroup;
            }

            @Override
            public int qos() {
                return qos;
            }

            @Override
            public void handle(MqttMessageContext message) {
            }
        };
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(2);
        }
        assertTrue(condition.getAsBoolean(), "condition was not met before timeout");
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final ExecutorService publishExecutor = Executors.newSingleThreadExecutor();
        private final ExecutorService handlerExecutor = Executors.newSingleThreadExecutor();
        private final ScheduledExecutorService connectionExecutor = Executors.newSingleThreadScheduledExecutor();
        private final List<FakeMqttClientAdapter> clients = new CopyOnWriteArrayList<>();
        private volatile Consumer<FakeMqttClientAdapter> clientInitializer = client -> { };
        private final MqttClientManager manager = new MqttClientManager(
                publishExecutor, handlerExecutor, connectionExecutor,
                new DefaultMqttResponseWaiter(), options -> {
                    FakeMqttClientAdapter client = new FakeMqttClientAdapter(options.resolveClientId());
                    clientInitializer.accept(client);
                    clients.add(client);
                    return client;
                });

        @Override
        public void close() {
            manager.close();
            publishExecutor.shutdownNow();
            handlerExecutor.shutdownNow();
            connectionExecutor.shutdownNow();
        }
    }
}

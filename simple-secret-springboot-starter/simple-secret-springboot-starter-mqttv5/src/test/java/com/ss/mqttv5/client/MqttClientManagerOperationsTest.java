package com.ss.mqttv5.client;

import com.ss.mqttv5.config.MqttClientOptions;
import com.ss.mqttv5.exception.MqttOperationException;
import com.ss.mqttv5.handler.MqttMessageHandler;
import com.ss.mqttv5.message.MqttMessageContext;
import com.ss.mqttv5.waiter.DefaultMqttResponseWaiter;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttClientManagerOperationsTest {

    @Test
    void publishesUtf8PayloadThroughDefaultClient() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();

            fixture.manager.publish("devices/a", "你好", 1);

            assertEquals(List.of("devices/a"), fixture.client().publishedTopics);
            MqttMessage message = fixture.client().publishedMessages.get(0);
            assertEquals("你好", new String(message.getPayload(), StandardCharsets.UTF_8));
            assertEquals(1, message.getQos());
            assertFalse(message.isRetained());
        } finally {
            fixture.close();
        }
    }

    @Test
    void validatesPublishArgumentsAndWrapsPahoFailureSafely() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.manager.publish("default", "devices/+", "payload", 0));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.manager.publish("default", "devices/a", "payload", 3));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.manager.publish("default", "devices/a", (MqttMessage) null));
            assertThrows(IllegalStateException.class,
                    () -> fixture.manager.publish("missing", "devices/a", "payload", 0));

            fixture.client().publishFailure = new MqttException(7);
            MqttOperationException error = assertThrows(MqttOperationException.class,
                    () -> fixture.manager.publish("default", "devices/a", "secret-payload", 0));
            assertFalse(error.getMessage().contains("secret-payload"));
            assertTrue(error.getCause() instanceof MqttException);
        } finally {
            fixture.close();
        }
    }

    @Test
    void subscribesSharedHandlerOnlyAfterPahoSuccess() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();
            MqttMessageHandler handler = handler("devices/+", "workers", 1);

            fixture.manager.subscribe("default", handler);

            assertEquals(1, fixture.client().subscriptions.size());
            assertEquals("$share/workers/devices/+", fixture.client().subscriptions.get(0).getTopic());
            assertEquals(1, fixture.client().subscriptions.get(0).getQos());
            assertEquals(List.of(handler), fixture.manager.getContext("default")
                    .getMatchingHandlers("devices/a"));

            fixture.manager.subscribe("default", handler);
            assertEquals(1, fixture.client().subscriptions.size());

            MqttMessageHandler failed = handler("failed/+", "", 0);
            fixture.client().subscribeFailure = new MqttException(8);
            assertThrows(MqttOperationException.class,
                    () -> fixture.manager.subscribe("default", failed));
            assertTrue(fixture.manager.getContext("default")
                    .getMatchingHandlers("failed/a").isEmpty());
        } finally {
            fixture.close();
        }
    }

    @Test
    void handlerIsRegisteredBeforeSubscribeCanDeliverRetainedMessage() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();
            CountDownLatch handled = new CountDownLatch(1);
            MqttMessageHandler handler = new MqttMessageHandler() {
                @Override
                public String topic() {
                    return "retained/+";
                }

                @Override
                public void handle(MqttMessageContext message) {
                    handled.countDown();
                }
            };
            fixture.client().subscribeHook = () -> {
                try {
                    fixture.client().arrive("retained/device", new MqttMessage("first".getBytes(StandardCharsets.UTF_8)));
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            };

            fixture.manager.subscribe("default", handler);

            assertTrue(handled.await(1, TimeUnit.SECONDS));
        } finally {
            fixture.close();
        }
    }

    @Test
    void sameWireFilterKeepsMaximumHandlerQos() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();
            MqttMessageHandler highQos = handler("devices/+", "workers", 2);
            MqttMessageHandler lowQos = handler("devices/+", "workers", 0);

            fixture.manager.subscribe("default", highQos);
            fixture.manager.subscribe("default", lowQos);

            assertEquals(1, fixture.client().subscriptions.size());
            assertEquals("$share/workers/devices/+",
                    fixture.client().subscriptions.get(0).getTopic());
            assertEquals(2, fixture.client().subscriptions.get(0).getQos());
            assertEquals(List.of(highQos, lowQos), fixture.manager.getContext("default")
                    .getMatchingHandlers("devices/a"));
        } finally {
            fixture.close();
        }
    }

    @Test
    void unsubscribeUsesWireFiltersAndCleansHandlersAfterSuccess() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();
            MqttMessageHandler shared = handler("devices/+", "workers", 1);
            fixture.manager.subscribe("default", shared);

            fixture.manager.unsubscribe("default", "devices/+");

            assertEquals(List.of("$share/workers/devices/+"), fixture.client().unsubscriptions);
            assertTrue(fixture.manager.getContext("default")
                    .getMatchingHandlers("devices/a").isEmpty());
        } finally {
            fixture.close();
        }
    }

    private MqttMessageHandler handler(String filter, String group, int qos) {
        return new MqttMessageHandler() {
            @Override
            public String topic() {
                return filter;
            }

            @Override
            public String shareGroup() {
                return group;
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

    private static final class Fixture implements AutoCloseable {
        private final ExecutorService publishExecutor = Executors.newSingleThreadExecutor();
        private final ExecutorService handlerExecutor = Executors.newSingleThreadExecutor();
        private final ScheduledExecutorService connectionExecutor = Executors.newSingleThreadScheduledExecutor();
        private final List<FakeMqttClientAdapter> clients = new CopyOnWriteArrayList<>();
        private final MqttClientManager manager = new MqttClientManager(
                publishExecutor, handlerExecutor, connectionExecutor,
                new DefaultMqttResponseWaiter(), options -> {
                    FakeMqttClientAdapter client = new FakeMqttClientAdapter(options.resolveClientId());
                    clients.add(client);
                    return client;
                });

        private void connect() throws InterruptedException {
            MqttClientOptions options = new MqttClientOptions();
            options.setEnabled(true);
            options.setBroker("tcp://test");
            options.setClientId("client-1");
            manager.refreshClients(Map.of("default", options), (key, value) -> { });
            await(() -> !clients.isEmpty() && clients.get(0).connected);
        }

        private FakeMqttClientAdapter client() {
            return clients.get(0);
        }

        private void await(BooleanSupplier condition) throws InterruptedException {
            long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
            while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
                Thread.sleep(2);
            }
            assertTrue(condition.getAsBoolean(), "condition was not met before timeout");
        }

        @Override
        public void close() {
            manager.close();
            publishExecutor.shutdownNow();
            handlerExecutor.shutdownNow();
            connectionExecutor.shutdownNow();
        }
    }
}

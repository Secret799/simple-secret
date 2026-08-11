package com.ss.mqttv3.client;

import com.ss.mqttv3.config.MqttClientOptions;
import com.ss.mqttv3.handler.MqttMessageHandler;
import com.ss.mqttv3.message.MqttMessageContext;
import com.ss.mqttv3.waiter.DefaultMqttResponseWaiter;
import com.ss.mqttv3.waiter.MqttCorrelationExtractor;
import com.ss.mqttv3.waiter.MqttResponseWaiter;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttClientManagerRequestTest {
    private static final MqttCorrelationExtractor PAYLOAD_EXTRACTOR = (type, payload) -> payload;

    @Test
    void republishesAfterEachTimeoutUpToTotalAttempts() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();

            Optional<MqttMessageContext> response = fixture.manager.requestWithRetry(
                    "default", "requests/device", "replies/device", "request-1", 1,
                    Duration.ZERO, PAYLOAD_EXTRACTOR, 3);

            assertTrue(response.isEmpty());
            assertEquals(List.of("requests/device", "requests/device", "requests/device"),
                    fixture.client().publishedTopics);
            assertEquals(1, fixture.client().subscriptions.size());
            assertEquals("replies/device", fixture.client().subscriptions.get(0).getTopic());
            assertEquals(List.of("replies/device"), fixture.client().unsubscriptions);
            assertTrue(fixture.responseWaiter.pendingKeys.isEmpty());
        } finally {
            fixture.close();
        }
    }

    @Test
    void successfulResponseStopsRetries() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();
            fixture.client().publishHook = (topic, message) ->
                    fixture.arriveUnchecked("replies/device", "request-1");

            Optional<MqttMessageContext> response = fixture.manager.requestWithRetry(
                    "default", "requests/device", "replies/device", "request-1", 0,
                    Duration.ofSeconds(1), PAYLOAD_EXTRACTOR, 3);

            assertEquals("request-1", response.orElseThrow().getPayloadAsString());
            assertEquals(1, fixture.client().publishedTopics.size());
            assertEquals(List.of("replies/device"), fixture.client().unsubscriptions);
        } finally {
            fixture.close();
        }
    }

    @Test
    void existingLongTermSubscriptionIsPreserved() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();
            MqttMessageHandler handler = fixture.handler("replies/device");
            fixture.manager.subscribe("default", handler);

            Optional<MqttMessageContext> response = fixture.manager.request(
                    "default", "requests/device", "replies/device", "request-1", 0,
                    Duration.ZERO, PAYLOAD_EXTRACTOR);

            assertTrue(response.isEmpty());
            assertEquals(1, fixture.client().subscriptions.size());
            assertTrue(fixture.client().unsubscriptions.isEmpty());
            assertEquals(List.of(handler), fixture.manager.getContext("default")
                    .getHandlers("replies/device"));
        } finally {
            fixture.close();
        }
    }

    @Test
    void requestRaisesExistingDirectSubscriptionToRequiredQos() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();
            fixture.manager.subscribe("default", fixture.handler("replies/device", "", 0));

            fixture.manager.request("default", "requests/device", "replies/device",
                    "request-1", 2, Duration.ZERO, PAYLOAD_EXTRACTOR);

            assertEquals(List.of(0, 2, 0), fixture.client().subscriptions.stream()
                    .map(subscription -> subscription.getQos()).toList());
            assertTrue(fixture.client().unsubscriptions.isEmpty());
        } finally {
            fixture.close();
        }
    }

    @Test
    void closingClientCancelsPendingRequestImmediately() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService requests = Executors.newSingleThreadExecutor();
        try {
            fixture.connect();
            Future<Optional<MqttMessageContext>> request = requests.submit(() -> fixture.manager.request(
                    "default", "requests/device", "replies/device", "request-1", 0,
                    Duration.ofSeconds(30), PAYLOAD_EXTRACTOR));
            fixture.await(() -> fixture.client().publishedTopics.size() == 1);

            fixture.manager.close("default");

            assertTrue(request.get(1, TimeUnit.SECONDS).isEmpty());
            assertTrue(fixture.responseWaiter.pendingKeys.isEmpty());
        } finally {
            requests.shutdownNow();
            fixture.close();
        }
    }

    @Test
    void explicitUnsubscribePreservesFilterOwnedByActiveRequest() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService requests = Executors.newSingleThreadExecutor();
        try {
            fixture.connect();
            fixture.manager.subscribe("default", fixture.handler("replies/device"));
            Future<Optional<MqttMessageContext>> request = requests.submit(() ->
                    fixture.manager.request("default", "requests/device", "replies/device",
                            "request-1", 0, Duration.ofSeconds(2), PAYLOAD_EXTRACTOR));
            fixture.await(() -> fixture.client().publishedTopics.size() == 1);

            fixture.manager.unsubscribe("default", "replies/device");

            assertTrue(fixture.client().activeSubscriptions.contains("replies/device"));
            fixture.client().arrive("replies/device", message("request-1"));
            assertEquals("request-1", request.get().orElseThrow().getPayloadAsString());
            fixture.await(() -> !fixture.client().activeSubscriptions.contains("replies/device"));
        } finally {
            requests.shutdownNow();
            fixture.close();
        }
    }

    @Test
    void concurrentRequestsReferenceCountOneTemporarySubscription() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService requests = Executors.newFixedThreadPool(2);
        try {
            fixture.connect();
            Future<Optional<MqttMessageContext>> first = requests.submit(() -> fixture.manager.request(
                    "default", "requests/device", "replies/+", "request-1", 0,
                    Duration.ofSeconds(2), PAYLOAD_EXTRACTOR));
            Future<Optional<MqttMessageContext>> second = requests.submit(() -> fixture.manager.request(
                    "default", "requests/device", "replies/+", "request-2", 0,
                    Duration.ofSeconds(2), PAYLOAD_EXTRACTOR));

            fixture.await(() -> fixture.client().publishedTopics.size() == 2);
            assertEquals(1, fixture.client().subscriptions.size());
            assertTrue(fixture.client().unsubscriptions.isEmpty());

            fixture.client().arrive("replies/device", message("request-1"));
            fixture.client().arrive("replies/device", message("request-2"));

            assertEquals("request-1", first.get().orElseThrow().getPayloadAsString());
            assertEquals("request-2", second.get().orElseThrow().getPayloadAsString());
            assertEquals(List.of("replies/+"), fixture.client().unsubscriptions);
        } finally {
            requests.shutdownNow();
            fixture.close();
        }
    }

    @Test
    void validatesArgumentsAndCleansRegistrationAfterPublishFailure() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();
            assertThrows(IllegalArgumentException.class, () -> fixture.manager.requestWithRetry(
                    "default", "requests/device", "replies/device", "request-1", 0,
                    Duration.ZERO, PAYLOAD_EXTRACTOR, 0));
            assertThrows(IllegalArgumentException.class, () -> fixture.manager.request(
                    "default", "requests/device", "replies/device", "request-1", 0,
                    Duration.ZERO, (type, payload) -> " "));
            assertTrue(fixture.client().subscriptions.isEmpty());

            fixture.client().publishFailure = new MqttException(7);
            assertThrows(RuntimeException.class, () -> fixture.manager.request(
                    "default", "requests/device", "replies/device", "request-1", 0,
                    Duration.ZERO, PAYLOAD_EXTRACTOR));

            assertTrue(fixture.responseWaiter.pendingKeys.isEmpty());
            assertEquals(List.of("replies/device"), fixture.client().unsubscriptions);
        } finally {
            fixture.close();
        }
    }

    @Test
    void longTermSubscribeCannotRaceWithTemporaryUnsubscribe() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService requests = Executors.newFixedThreadPool(2);
        try {
            fixture.connect();
            CountDownLatch unsubscribeEntered = new CountDownLatch(1);
            CountDownLatch allowUnsubscribe = new CountDownLatch(1);
            CountDownLatch longTermSubscribeCalled = new CountDownLatch(1);
            fixture.client().unsubscribeHook = () -> {
                unsubscribeEntered.countDown();
                awaitUnchecked(allowUnsubscribe);
            };
            Future<?> request = requests.submit(() -> fixture.manager.request(
                    "default", "requests/device", "replies/device", "request-1", 0,
                    Duration.ZERO, PAYLOAD_EXTRACTOR));
            assertTrue(unsubscribeEntered.await(1, TimeUnit.SECONDS));

            fixture.client().subscribeHook = longTermSubscribeCalled::countDown;
            MqttMessageHandler handler = fixture.handler("replies/device");
            Future<?> subscription = requests.submit(
                    () -> fixture.manager.subscribe("default", handler));
            longTermSubscribeCalled.await(100, TimeUnit.MILLISECONDS);
            allowUnsubscribe.countDown();

            request.get();
            subscription.get();
            assertEquals(List.of(handler), fixture.manager.getContext("default")
                    .getHandlers("replies/device"));
            assertTrue(fixture.client().activeSubscriptions.contains("replies/device"));
        } finally {
            requests.shutdownNow();
            fixture.close();
        }
    }

    @Test
    void reconnectRestoresTemporaryReplySubscription() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService requests = Executors.newSingleThreadExecutor();
        try {
            fixture.connect();
            fixture.manager.getContext("default").getOptions().setReconnectDelayMillis(10);
            Future<Optional<MqttMessageContext>> request = requests.submit(() -> fixture.manager.request(
                    "default", "requests/device", "replies/device", "request-1", 1,
                    Duration.ofSeconds(2), PAYLOAD_EXTRACTOR));
            fixture.await(() -> fixture.client().publishedTopics.size() == 1);

            fixture.client().disconnectUnexpectedly();

            fixture.await(() -> fixture.client().connectCount == 2
                    && fixture.client().activeSubscriptions.contains("replies/device"));
            fixture.client().arrive("replies/device", message("request-1"));
            assertEquals("request-1", request.get().orElseThrow().getPayloadAsString());
        } finally {
            requests.shutdownNow();
            fixture.close();
        }
    }

    @Test
    void sharedHandlerDoesNotReplacePlainTemporaryReplySubscription() throws Exception {
        Fixture fixture = new Fixture();
        try {
            fixture.connect();
            MqttMessageHandler sharedHandler = fixture.handler("replies/+", "workers");
            fixture.manager.subscribe("default", sharedHandler);

            Optional<MqttMessageContext> response = fixture.manager.request(
                    "default", "requests/device", "replies/+", "request-1", 1,
                    Duration.ZERO, PAYLOAD_EXTRACTOR);

            assertTrue(response.isEmpty());
            assertEquals(List.of("$share/workers/replies/+", "replies/+"),
                    fixture.client().subscriptions.stream()
                            .map(subscription -> subscription.getTopic()).toList());
            assertEquals(List.of("replies/+"), fixture.client().unsubscriptions);
            assertTrue(fixture.client().activeSubscriptions
                    .contains("$share/workers/replies/+"));
            assertTrue(!fixture.client().activeSubscriptions.contains("replies/+"));
        } finally {
            fixture.close();
        }
    }

    private static MqttMessage message(String payload) {
        return new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
        private final ExecutorService publishExecutor = Executors.newFixedThreadPool(2);
        private final ExecutorService handlerExecutor = Executors.newSingleThreadExecutor();
        private final ScheduledExecutorService connectionExecutor = Executors.newSingleThreadScheduledExecutor();
        private final List<FakeMqttClientAdapter> clients = new CopyOnWriteArrayList<>();
        private final TrackingResponseWaiter responseWaiter = new TrackingResponseWaiter();
        private final MqttClientManager manager = new MqttClientManager(
                publishExecutor, handlerExecutor, connectionExecutor,
                responseWaiter, options -> {
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

        private MqttMessageHandler handler(String filter) {
            return handler(filter, "", 0);
        }

        private MqttMessageHandler handler(String filter, String shareGroup) {
            return handler(filter, shareGroup, 0);
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

        private void arriveUnchecked(String topic, String payload) {
            try {
                client().arrive(topic, message(payload));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
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

    private static final class TrackingResponseWaiter implements MqttResponseWaiter {
        private final DefaultMqttResponseWaiter delegate = new DefaultMqttResponseWaiter();
        private final Set<String> pendingKeys = ConcurrentHashMap.newKeySet();

        @Override
        public void register(String waitKey) {
            delegate.register(waitKey);
            pendingKeys.add(waitKey);
        }

        @Override
        public Optional<MqttMessageContext> await(String waitKey, Duration timeout) {
            try {
                return delegate.await(waitKey, timeout);
            } finally {
                pendingKeys.remove(waitKey);
            }
        }

        @Override
        public boolean complete(String waitKey, MqttMessageContext message) {
            return delegate.complete(waitKey, message);
        }

        @Override
        public void cancel(String waitKey) {
            delegate.cancel(waitKey);
            pendingKeys.remove(waitKey);
        }
    }
}

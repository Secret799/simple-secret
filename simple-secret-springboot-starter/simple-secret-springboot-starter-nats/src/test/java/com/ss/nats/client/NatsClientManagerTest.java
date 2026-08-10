package com.ss.nats.client;

import com.ss.nats.config.NatsClientOptions;
import com.ss.nats.exception.NatsOperationException;
import com.ss.nats.handler.NatsMessageHandler;
import com.ss.nats.handler.NatsMessageValidator;
import com.ss.nats.message.NatsMessageContext;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Options;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatsClientManagerTest {

    private final ExecutorService publishExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService handlerExecutor = Executors.newSingleThreadExecutor();

    @AfterEach
    void shutdownExecutors() {
        publishExecutor.shutdownNow();
        handlerExecutor.shutdownNow();
    }

    @Test
    void refreshShouldReuseUnchangedClientAndReplaceChangedClient() {
        RecordingFactory factory = new RecordingFactory();
        NatsClientManager manager = new NatsClientManager(publishExecutor, handlerExecutor, factory);
        NatsClientOptions options = enabledOptions("nats://localhost:4222");
        AtomicInteger callbacks = new AtomicInteger();

        manager.refreshClients(Map.of("edge", options), (key, configured) -> callbacks.incrementAndGet());
        manager.refreshClients(Map.of("edge", options), (key, configured) -> callbacks.incrementAndGet());
        options.setUrl("nats://localhost:5222");
        manager.refreshClients(Map.of("edge", options), (key, configured) -> callbacks.incrementAndGet());

        assertThat(factory.connections).isEqualTo(2);
        assertThat(factory.first.closed).isTrue();
        assertThat(callbacks).hasValue(2);
        assertThat(manager.containsClient("edge")).isTrue();
    }

    @Test
    void refreshShouldReplaceClosedConnectionEvenWhenConfigurationIsUnchanged() {
        RecordingFactory factory = new RecordingFactory();
        NatsClientManager manager = new NatsClientManager(publishExecutor, handlerExecutor, factory);
        NatsClientOptions options = enabledOptions("nats://localhost:4222");

        manager.refreshClients(Map.of("edge", options), (key, configured) -> { });
        factory.latest.status = Connection.Status.CLOSED;
        manager.refreshClients(Map.of("edge", options), (key, configured) -> { });

        assertThat(factory.connections).isEqualTo(2);
        assertThat(factory.first.closed).isTrue();
        assertThat(factory.latest.status).isEqualTo(Connection.Status.CONNECTED);
    }

    @Test
    void publishShouldFlushAndFailClearlyWhenDisconnected() {
        RecordingFactory factory = new RecordingFactory();
        NatsClientManager manager = new NatsClientManager(publishExecutor, handlerExecutor, factory);
        manager.refreshClients(Map.of("edge", enabledOptions("nats://localhost:4222")), (key, options) -> { });

        manager.publish("edge", "events.created", "hello");

        assertThat(factory.latest.published.getSubject()).isEqualTo("events.created");
        assertThat(new String(factory.latest.published.getData(), StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(factory.latest.flushTimeout).isEqualTo(Duration.ofSeconds(10));

        factory.latest.status = Connection.Status.DISCONNECTED;
        assertThatThrownBy(() -> manager.publish("edge", "events.created", "again"))
                .isInstanceOf(NatsOperationException.class)
                .hasMessageContaining("edge", "publish");
    }

    @Test
    void requestShouldReturnSnapshotOrEmptyOnTimeout() {
        RecordingFactory factory = new RecordingFactory();
        factory.nextResponse = NatsMessage.builder().subject("reply").data("ok").build();
        NatsClientManager manager = new NatsClientManager(publishExecutor, handlerExecutor, factory);
        manager.refreshClients(Map.of("edge", enabledOptions("nats://localhost:4222")), (key, options) -> { });

        Optional<NatsMessageContext> response = manager.request(
                "edge", "request.subject", "payload".getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(2));
        factory.latest.response = null;
        Optional<NatsMessageContext> timeout = manager.request(
                "edge", "request.subject", new byte[0], Duration.ofMillis(10));

        assertThat(response).get().extracting(NatsMessageContext::getPayloadAsString).isEqualTo("ok");
        assertThat(timeout).isEmpty();
    }

    @Test
    void subscribeShouldUseQueueAndRunValidatorBeforeHandler() throws Exception {
        RecordingFactory factory = new RecordingFactory();
        NatsClientManager manager = new NatsClientManager(publishExecutor, handlerExecutor, factory);
        manager.refreshClients(Map.of("edge", enabledOptions("nats://localhost:4222")), (key, options) -> { });
        AtomicInteger handled = new AtomicInteger();
        class ValidatingHandler implements NatsMessageHandler, NatsMessageValidator {
            private boolean valid;
            @Override public String clientKey() { return "edge"; }
            @Override public String subject() { return "devices.*.state"; }
            @Override public String queue() { return "workers"; }
            @Override public boolean ordered() { return true; }
            @Override public boolean validate(NatsMessageContext message) { return valid; }
            @Override public void handle(NatsMessageContext message) { handled.incrementAndGet(); }
        }
        ValidatingHandler handler = new ValidatingHandler();

        manager.subscribe("edge", handler);
        factory.latest.messageHandler.onMessage(
                NatsMessage.builder().subject("devices.a.state").data("one").build());
        handler.valid = true;
        factory.latest.messageHandler.onMessage(
                NatsMessage.builder().subject("devices.a.state").data("two").build());

        assertThat(factory.latest.subscribedSubject).isEqualTo("devices.*.state");
        assertThat(factory.latest.subscribedQueue).isEqualTo("workers");
        assertThat(handled).hasValue(1);
    }

    @Test
    void closeShouldCloseDispatchersDrainAndCloseConnection() {
        RecordingFactory factory = new RecordingFactory();
        NatsClientManager manager = new NatsClientManager(publishExecutor, handlerExecutor, factory);
        manager.refreshClients(Map.of("edge", enabledOptions("nats://localhost:4222")), (key, options) -> { });
        manager.subscribe("edge", new NatsMessageHandler() {
            @Override public String subject() { return "events.>"; }
            @Override public void handle(NatsMessageContext message) { }
        });

        manager.close("edge");

        assertThat(factory.latest.closedDispatchers).isEqualTo(1);
        assertThat(factory.latest.drained).isTrue();
        assertThat(factory.latest.closed).isTrue();
        assertThat(manager.containsClient("edge")).isFalse();
    }

    @Test
    void closeShouldWaitForDrainCompletionBeforeClosingConnection() throws Exception {
        RecordingFactory factory = new RecordingFactory();
        factory.nextDrain = new CompletableFuture<>();
        NatsClientManager manager = new NatsClientManager(publishExecutor, handlerExecutor, factory);
        manager.refreshClients(Map.of("edge", enabledOptions("nats://localhost:4222")),
                (key, options) -> { });
        ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
        try {
            Future<?> close = closeExecutor.submit(() -> manager.close("edge"));

            assertThat(factory.latest.drainCalled.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(close).isNotDone();
            assertThat(factory.latest.closed).isFalse();

            factory.nextDrain.complete(true);
            close.get(1, TimeUnit.SECONDS);
            assertThat(factory.latest.closed).isTrue();
        } finally {
            closeExecutor.shutdownNow();
        }
    }

    private static NatsClientOptions enabledOptions(String url) {
        NatsClientOptions options = new NatsClientOptions();
        options.setEnabled(true);
        options.setUrl(url);
        return options;
    }

    private static final class RecordingFactory implements NatsConnectionFactory {
        private int connections;
        private RecordingConnection first;
        private RecordingConnection latest;
        private Message nextResponse;
        private CompletableFuture<Boolean> nextDrain = CompletableFuture.completedFuture(true);

        @Override
        public Connection connect(Options options) {
            RecordingConnection recording = new RecordingConnection();
            recording.response = nextResponse;
            recording.drainFuture = nextDrain;
            if (first == null) first = recording;
            latest = recording;
            connections++;
            return recording.proxy();
        }
    }

    private static final class RecordingConnection implements InvocationHandler {
        private Connection.Status status = Connection.Status.CONNECTED;
        private Message published;
        private Message response;
        private Duration flushTimeout;
        private MessageHandler messageHandler;
        private String subscribedSubject;
        private String subscribedQueue;
        private int closedDispatchers;
        private boolean drained;
        private boolean closed;
        private CompletableFuture<Boolean> drainFuture = CompletableFuture.completedFuture(true);
        private final CountDownLatch drainCalled = new CountDownLatch(1);

        private Connection proxy() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getStatus" -> status;
                case "publish" -> { published = args.length == 1 ? (Message) args[0]
                        : NatsMessage.builder().subject((String) args[0]).data((byte[]) args[1]).build(); yield null; }
                case "flush" -> { flushTimeout = (Duration) args[0]; yield null; }
                case "request" -> response;
                case "createDispatcher" -> {
                    messageHandler = (MessageHandler) args[0];
                    yield dispatcher();
                }
                case "closeDispatcher" -> { closedDispatchers++; yield null; }
                case "drain" -> { drained = true; drainCalled.countDown(); yield drainFuture; }
                case "close" -> { closed = true; status = Connection.Status.CLOSED; yield null; }
                case "toString" -> "RecordingConnection";
                default -> defaultValue(method.getReturnType());
            };
        }

        private Dispatcher dispatcher() {
            return (Dispatcher) Proxy.newProxyInstance(
                    Dispatcher.class.getClassLoader(), new Class<?>[]{Dispatcher.class},
                    (proxy, method, args) -> {
                        if ("subscribe".equals(method.getName())) {
                            subscribedSubject = (String) args[0];
                            subscribedQueue = args.length > 1 ? (String) args[1] : "";
                            return proxy;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            return 0D;
        }
    }
}

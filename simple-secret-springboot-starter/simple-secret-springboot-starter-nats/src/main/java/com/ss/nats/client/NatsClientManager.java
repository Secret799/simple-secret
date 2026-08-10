package com.ss.nats.client;

import com.ss.nats.config.NatsClientOptions;
import com.ss.nats.exception.NatsOperationException;
import com.ss.nats.handler.NatsMessageHandler;
import com.ss.nats.handler.NatsMessageValidator;
import com.ss.nats.message.NatsMessageContext;
import com.ss.nats.subject.NatsSubjects;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/**
 * 管理多个具名 NATS 连接以及它们的发布、请求和订阅生命周期。
 */
public class NatsClientManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NatsClientManager.class);

    private final ExecutorService publishExecutor;
    private final ExecutorService handlerExecutor;
    private final NatsConnectionFactory connectionFactory;
    private final Map<String, ClientContext> clients = new HashMap<>();

    /**
     * 使用 JNATS 默认同步连接工厂创建管理器。
     *
     * @param publishExecutor 发布执行器
     * @param handlerExecutor 非有序消息处理执行器
     */
    public NatsClientManager(ExecutorService publishExecutor, ExecutorService handlerExecutor) {
        this(publishExecutor, handlerExecutor, Nats::connect);
    }

    NatsClientManager(ExecutorService publishExecutor, ExecutorService handlerExecutor,
                      NatsConnectionFactory connectionFactory) {
        this.publishExecutor = Objects.requireNonNull(publishExecutor, "publishExecutor");
        this.handlerExecutor = Objects.requireNonNull(handlerExecutor, "handlerExecutor");
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    /**
     * 使当前连接集合与配置一致。未变化的客户端会被复用，禁用或删除的客户端会被关闭。
     *
     * @param configuredClients 以客户端键为索引的配置
     * @param onConnected 新连接注册成功后的回调
     */
    public synchronized void refreshClients(Map<String, NatsClientOptions> configuredClients,
                                            BiConsumer<String, NatsClientOptions> onConnected) {
        Map<String, NatsClientOptions> source = configuredClients == null ? Map.of() : configuredClients;
        BiConsumer<String, NatsClientOptions> callback = Objects.requireNonNull(onConnected, "onConnected");

        List<String> removedKeys = clients.keySet().stream()
                .filter(key -> !isEnabled(source.get(key)))
                .toList();
        removedKeys.forEach(this::close);

        source.forEach((clientKey, options) -> {
            Objects.requireNonNull(options, "NATS client options for " + clientKey);
            options.validate(clientKey);
            if (!options.isEnabled()) {
                return;
            }
            ClientSettings settings = ClientSettings.from(clientKey, options);
            ClientContext existing = clients.get(clientKey);
            if (existing != null && existing.settings.equals(settings)
                    && isReusable(existing.connection)) {
                return;
            }
            ClientContext replacement = connect(clientKey, settings);
            try {
                clients.put(clientKey, replacement);
                callback.accept(clientKey, settings.toOptions());
            } catch (RuntimeException exception) {
                clients.remove(clientKey, replacement);
                closeContext(clientKey, replacement);
                if (existing != null) {
                    clients.put(clientKey, existing);
                }
                throw new NatsOperationException(operationMessage(clientKey, "connect callback"), exception);
            }
            if (existing != null) {
                closeContext(clientKey, existing);
            }
        });
    }

    /** @return 是否存在指定客户端。 */
    public synchronized boolean containsClient(String clientKey) {
        return clients.containsKey(clientKey);
    }

    /** 使用 UTF-8 发布文本消息并等待 flush 完成。 */
    public void publish(String clientKey, String subject, String payload) {
        Objects.requireNonNull(payload, "payload");
        publish(clientKey, subject, payload.getBytes(StandardCharsets.UTF_8));
    }

    /** 发布字节消息并等待 flush 完成。 */
    public void publish(String clientKey, String subject, byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        NatsSubjects.validatePublishSubject(subject);
        ClientContext context = connectedContext(clientKey, "publish");
        byte[] snapshot = payload.clone();
        runPublish(clientKey, context, () -> context.connection.publish(subject, snapshot));
    }

    /** 发布调用方构造的 JNATS 消息并等待 flush 完成。 */
    public void publish(String clientKey, Message message) {
        Message source = Objects.requireNonNull(message, "message");
        NatsSubjects.validatePublishSubject(source.getSubject());
        ClientContext context = connectedContext(clientKey, "publish");
        runPublish(clientKey, context, () -> context.connection.publish(source));
    }

    /**
     * 执行同步请求响应。超时或没有响应时返回空值。
     */
    public Optional<NatsMessageContext> request(String clientKey, String subject, byte[] payload,
                                                Duration timeout) {
        Objects.requireNonNull(payload, "payload");
        Duration effectiveTimeout = requirePositive(timeout, "request timeout");
        NatsSubjects.validatePublishSubject(subject);
        ClientContext context = connectedContext(clientKey, "request");
        try {
            Message response = context.connection.request(subject, payload.clone(), effectiveTimeout);
            return response == null ? Optional.empty() : Optional.of(new NatsMessageContext(clientKey, response));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NatsOperationException(operationMessage(clientKey, "request"), exception);
        } catch (RuntimeException exception) {
            throw new NatsOperationException(operationMessage(clientKey, "request"), exception);
        }
    }

    /** 使用客户端配置的默认超时执行请求响应。 */
    public Optional<NatsMessageContext> request(String clientKey, String subject, byte[] payload) {
        ClientContext context = context(clientKey, "request");
        return request(clientKey, subject, payload, context.settings.requestTimeout);
    }

    /**
     * 注册一个消息处理器。返回的 Dispatcher 会在客户端关闭时一并释放。
     */
    public synchronized Dispatcher subscribe(String clientKey, NatsMessageHandler handler) {
        NatsMessageHandler target = Objects.requireNonNull(handler, "handler");
        NatsSubjects.validateSubscriptionSubject(target.subject());
        NatsSubjects.validateQueue(target.queue());
        ClientContext context = connectedContext(clientKey, "subscribe");
        try {
            Dispatcher dispatcher = context.connection.createDispatcher(message ->
                    dispatch(clientKey, target, new NatsMessageContext(clientKey, message)));
            if (target.queue() == null || target.queue().isBlank()) {
                dispatcher.subscribe(target.subject());
            } else {
                dispatcher.subscribe(target.subject(), target.queue());
            }
            context.dispatchers.add(dispatcher);
            return dispatcher;
        } catch (RuntimeException exception) {
            throw new NatsOperationException(operationMessage(clientKey, "subscribe"), exception);
        }
    }

    /** 关闭并移除指定客户端。不存在时不执行任何操作。 */
    public synchronized void close(String clientKey) {
        ClientContext context = clients.remove(clientKey);
        if (context != null) {
            closeContext(clientKey, context);
        }
    }

    /** 关闭所有连接，执行器由其创建方负责关闭。 */
    @Override
    public synchronized void close() {
        List<Map.Entry<String, ClientContext>> entries = new ArrayList<>(clients.entrySet());
        clients.clear();
        NatsOperationException firstFailure = null;
        for (Map.Entry<String, ClientContext> entry : entries) {
            try {
                closeContext(entry.getKey(), entry.getValue());
            } catch (NatsOperationException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private ClientContext connect(String clientKey, ClientSettings settings) {
        try {
            Connection connection = connectionFactory.connect(settings.toJnatOptions());
            return new ClientContext(connection, settings);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NatsOperationException(operationMessage(clientKey, "connect"), exception);
        } catch (Exception exception) {
            throw new NatsOperationException(operationMessage(clientKey, "connect"), exception);
        }
    }

    private void runPublish(String clientKey, ClientContext context, PublishAction action) {
        Future<?> future;
        try {
            future = publishExecutor.submit(() -> {
                action.publish();
                context.connection.flush(context.settings.publishTimeout);
                return null;
            });
        } catch (RejectedExecutionException exception) {
            throw new NatsOperationException(operationMessage(clientKey, "publish"), exception);
        }
        try {
            future.get(context.settings.publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new NatsOperationException(operationMessage(clientKey, "publish"), exception);
        } catch (ExecutionException | TimeoutException exception) {
            future.cancel(true);
            Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                    ? exception.getCause() : exception;
            throw new NatsOperationException(operationMessage(clientKey, "publish"), cause);
        }
    }

    private void dispatch(String clientKey, NatsMessageHandler handler, NatsMessageContext message) {
        Runnable task = () -> {
            try {
                if (handler instanceof NatsMessageValidator validator && !validator.validate(message)) {
                    LOGGER.warn("NATS message validation rejected clientKey={} subject={}",
                            clientKey, message.getSubject());
                    return;
                }
                handler.handle(message);
            } catch (RuntimeException exception) {
                LOGGER.error("NATS message handling failed clientKey={} subject={}",
                        clientKey, message.getSubject(), exception);
            }
        };
        if (handler.ordered()) {
            task.run();
            return;
        }
        try {
            handlerExecutor.execute(task);
        } catch (RejectedExecutionException exception) {
            LOGGER.error("NATS handler executor rejected message clientKey={} subject={}",
                    clientKey, message.getSubject(), exception);
        }
    }

    private void closeContext(String clientKey, ClientContext context) {
        NatsOperationException failure = null;
        for (Dispatcher dispatcher : new ArrayList<>(context.dispatchers)) {
            try {
                context.connection.closeDispatcher(dispatcher);
            } catch (RuntimeException exception) {
                failure = appendFailure(failure, clientKey, "close dispatcher", exception);
            }
        }
        context.dispatchers.clear();
        try {
            Future<Boolean> drain = context.connection.drain(context.settings.publishTimeout);
            Boolean completed = drain.get(
                    context.settings.publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!Boolean.TRUE.equals(completed)) {
                failure = appendFailure(failure, clientKey, "drain",
                        new IllegalStateException("NATS drain did not complete successfully"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failure = appendFailure(failure, clientKey, "drain", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            failure = appendFailure(failure, clientKey, "drain", cause);
        } catch (TimeoutException | RuntimeException exception) {
            failure = appendFailure(failure, clientKey, "drain", exception);
        }
        try {
            context.connection.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failure = appendFailure(failure, clientKey, "close", exception);
        } catch (RuntimeException exception) {
            failure = appendFailure(failure, clientKey, "close", exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private synchronized ClientContext connectedContext(String clientKey, String operation) {
        ClientContext context = context(clientKey, operation);
        if (context.connection.getStatus() != Connection.Status.CONNECTED) {
            throw new NatsOperationException(operationMessage(clientKey, operation)
                    + ": client is not connected");
        }
        return context;
    }

    private synchronized ClientContext context(String clientKey, String operation) {
        ClientContext context = clients.get(clientKey);
        if (context == null) {
            throw new NatsOperationException(operationMessage(clientKey, operation)
                    + ": client is not configured");
        }
        return context;
    }

    private static boolean isEnabled(NatsClientOptions options) {
        return options != null && options.isEnabled();
    }

    private static boolean isReusable(Connection connection) {
        Connection.Status status = connection.getStatus();
        return status == Connection.Status.CONNECTED || status == Connection.Status.RECONNECTING;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String operationMessage(String clientKey, String operation) {
        return "NATS " + operation + " failed for client " + clientKey;
    }

    private static NatsOperationException appendFailure(NatsOperationException current,
                                                        String clientKey, String operation,
                                                        Throwable cause) {
        NatsOperationException next = new NatsOperationException(operationMessage(clientKey, operation), cause);
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    @FunctionalInterface
    private interface PublishAction {
        void publish();
    }

    private static final class ClientContext {
        private final Connection connection;
        private final ClientSettings settings;
        private final List<Dispatcher> dispatchers = new ArrayList<>();

        private ClientContext(Connection connection, ClientSettings settings) {
            this.connection = Objects.requireNonNull(connection, "connection");
            this.settings = settings;
        }
    }

    /** 不提供 toString，避免配置诊断时意外暴露密码。 */
    private static final class ClientSettings {
        private final String clientKey;
        private final String url;
        private final String connectionName;
        private final String username;
        private final String password;
        private final boolean reconnectEnabled;
        private final int maxReconnects;
        private final Duration reconnectWait;
        private final Duration reconnectJitter;
        private final Duration connectionTimeout;
        private final Duration publishTimeout;
        private final Duration requestTimeout;

        private ClientSettings(String clientKey, String url, String connectionName,
                               String username, String password, boolean reconnectEnabled,
                               int maxReconnects, Duration reconnectWait, Duration reconnectJitter,
                               Duration connectionTimeout, Duration publishTimeout,
                               Duration requestTimeout) {
            this.clientKey = clientKey;
            this.url = url;
            this.connectionName = connectionName;
            this.username = username;
            this.password = password;
            this.reconnectEnabled = reconnectEnabled;
            this.maxReconnects = maxReconnects;
            this.reconnectWait = reconnectWait;
            this.reconnectJitter = reconnectJitter;
            this.connectionTimeout = connectionTimeout;
            this.publishTimeout = publishTimeout;
            this.requestTimeout = requestTimeout;
        }

        private static ClientSettings from(String clientKey, NatsClientOptions options) {
            return new ClientSettings(
                    clientKey, options.getUrl().trim(), options.resolveConnectionName(clientKey),
                    trimToNull(options.getUsername()), options.getPassword(), options.isReconnectEnabled(),
                    options.getMaxReconnects(), Duration.ofMillis(options.getReconnectWaitMillis()),
                    Duration.ofMillis(options.getReconnectJitterMillis()),
                    Duration.ofMillis(options.getConnectionTimeoutMillis()),
                    Duration.ofMillis(options.getPublishTimeoutMillis()),
                    Duration.ofMillis(options.getRequestTimeoutMillis()));
        }

        private Options toJnatOptions() {
            Options.Builder builder = new Options.Builder()
                    .server(url)
                    .connectionName(connectionName)
                    .connectionTimeout(connectionTimeout);
            if (reconnectEnabled) {
                builder.maxReconnects(maxReconnects)
                        .reconnectWait(reconnectWait)
                        .reconnectJitter(reconnectJitter);
            } else {
                builder.noReconnect();
            }
            if (username != null) {
                builder.userInfo(username, password == null ? "" : password);
            }
            return builder.build();
        }

        private NatsClientOptions toOptions() {
            NatsClientOptions copy = new NatsClientOptions();
            copy.setEnabled(true);
            copy.setUrl(url);
            copy.setConnectionName(connectionName);
            copy.setUsername(username);
            copy.setPassword(password);
            copy.setReconnectEnabled(reconnectEnabled);
            copy.setMaxReconnects(maxReconnects);
            copy.setReconnectWaitMillis(reconnectWait.toMillis());
            copy.setReconnectJitterMillis(reconnectJitter.toMillis());
            copy.setConnectionTimeoutMillis(connectionTimeout.toMillis());
            copy.setPublishTimeoutMillis(publishTimeout.toMillis());
            copy.setRequestTimeoutMillis(requestTimeout.toMillis());
            return copy;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof ClientSettings other)) {
                return false;
            }
            return reconnectEnabled == other.reconnectEnabled
                    && maxReconnects == other.maxReconnects
                    && Objects.equals(clientKey, other.clientKey)
                    && Objects.equals(url, other.url)
                    && Objects.equals(connectionName, other.connectionName)
                    && Objects.equals(username, other.username)
                    && Objects.equals(password, other.password)
                    && Objects.equals(reconnectWait, other.reconnectWait)
                    && Objects.equals(reconnectJitter, other.reconnectJitter)
                    && Objects.equals(connectionTimeout, other.connectionTimeout)
                    && Objects.equals(publishTimeout, other.publishTimeout)
                    && Objects.equals(requestTimeout, other.requestTimeout);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clientKey, url, connectionName, username, password, reconnectEnabled,
                    maxReconnects, reconnectWait, reconnectJitter, connectionTimeout,
                    publishTimeout, requestTimeout);
        }

        private static String trimToNull(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim();
        }
    }
}

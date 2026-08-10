package com.ss.mqttv5.client;

import com.ss.mqttv5.config.MqttClientOptions;
import com.ss.mqttv5.config.MqttWillOptions;
import com.ss.mqttv5.exception.MqttOperationException;
import com.ss.mqttv5.handler.MqttMessageHandler;
import com.ss.mqttv5.message.MqttMessageContext;
import com.ss.mqttv5.topic.MqttTopics;
import com.ss.mqttv5.waiter.MqttCorrelationExtractor;
import com.ss.mqttv5.waiter.MqttCorrelationType;
import com.ss.mqttv5.waiter.MqttResponseWaiter;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * 管理多个 MQTT v5 客户端的连接与生命周期。
 */
public class MqttClientManager implements AutoCloseable {
    /** 默认客户端键。 */
    public static final String DEFAULT_CLIENT_KEY = "default";

    private static final Logger LOG = LoggerFactory.getLogger(MqttClientManager.class);

    private final ExecutorService publishExecutor;
    private final ExecutorService handlerExecutor;
    private final ScheduledExecutorService connectionExecutor;
    private final MqttResponseWaiter responseWaiter;
    private final MqttClientFactory clientFactory;
    private final Map<String, MqttClientContext> clients = new ConcurrentHashMap<>();
    private final Map<String, String> fingerprints = new ConcurrentHashMap<>();
    private final Map<String, ReconnectTask> reconnectTasks = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<String, MqttClientOptions>> connectedHandlers =
            new ConcurrentHashMap<>();
    private final Map<String, Map<String, AtomicInteger>> temporarySubscriptionCounters =
            new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> temporarySubscriptionQos =
            new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<MqttMessageHandler>> handlerRegistrations =
            new ConcurrentHashMap<>();

    /**
     * 使用默认 Paho 客户端工厂创建管理器。
     *
     * @param publishExecutor    发布执行器
     * @param handlerExecutor    消息处理执行器
     * @param connectionExecutor 连接与重连调度执行器
     * @param responseWaiter     同步响应等待器
     */
    public MqttClientManager(ExecutorService publishExecutor,
                             ExecutorService handlerExecutor,
                             ScheduledExecutorService connectionExecutor,
                             MqttResponseWaiter responseWaiter) {
        this(publishExecutor, handlerExecutor, connectionExecutor,
                responseWaiter, PahoMqttClientAdapter::new);
    }

    MqttClientManager(ExecutorService publishExecutor,
                      ExecutorService handlerExecutor,
                      ScheduledExecutorService connectionExecutor,
                      MqttResponseWaiter responseWaiter,
                      MqttClientFactory clientFactory) {
        this.publishExecutor = Objects.requireNonNull(publishExecutor, "publishExecutor");
        this.handlerExecutor = Objects.requireNonNull(handlerExecutor, "handlerExecutor");
        this.connectionExecutor = Objects.requireNonNull(connectionExecutor, "connectionExecutor");
        this.responseWaiter = Objects.requireNonNull(responseWaiter, "responseWaiter");
        this.clientFactory = Objects.requireNonNull(clientFactory, "clientFactory");
    }

    /**
     * 按完整的启用客户端配置刷新当前连接集合。
     *
     * @param enabledClients 启用客户端，键为 clientKey
     * @param onConnected    每次连接或重连成功后的回调
     */
    public synchronized void refreshClients(
            Map<String, MqttClientOptions> enabledClients,
            BiConsumer<String, MqttClientOptions> onConnected) {
        Objects.requireNonNull(enabledClients, "enabledClients");
        Objects.requireNonNull(onConnected, "onConnected");
        enabledClients.forEach(this::validateClient);

        for (String clientKey : new ArrayList<>(clients.keySet())) {
            if (!enabledClients.containsKey(clientKey)) {
                close(clientKey, true);
            }
        }

        enabledClients.forEach((clientKey, options) -> {
            String fingerprint = fingerprint(options);
            MqttClientContext current = clients.get(clientKey);
            connectedHandlers.put(clientKey, onConnected);
            if (current != null && fingerprint.equals(fingerprints.get(clientKey))) {
                return;
            }
            if (current != null) {
                close(clientKey, false);
                connectedHandlers.put(clientKey, onConnected);
            }
            createClient(clientKey, options, fingerprint);
        });
    }

    /**
     * 判断客户端键是否已受管理。
     *
     * @param clientKey 客户端键
     * @return 已创建上下文时返回 {@code true}
     */
    public boolean containsClient(String clientKey) {
        return clients.containsKey(clientKey);
    }

    /**
     * 判断客户端是否已连接。
     *
     * @param clientKey 客户端键
     * @return 已连接时返回 {@code true}
     */
    public boolean isConnected(String clientKey) {
        MqttClientContext context = clients.get(clientKey);
        return context != null && context.getClient().isConnected();
    }

    /**
     * 使用指定客户端发布 Paho 消息。
     *
     * @param clientKey 客户端键
     * @param topic     发布主题
     * @param message   Paho 消息
     */
    public void publish(String clientKey, String topic, MqttMessage message) {
        MqttTopics.validateTopic(topic);
        if (message == null) {
            throw new IllegalArgumentException("MQTT message must not be null");
        }
        MqttClientContext context = requireConnectedContext(clientKey);
        Future<Void> future;
        try {
            future = publishExecutor.submit(() -> {
                context.getClient().publish(topic, message);
                return null;
            });
        } catch (RuntimeException e) {
            throw new MqttOperationException("publish", clientKey, topic, e);
        }
        try {
            future.get(context.getOptions().getPublishTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new MqttOperationException("publish", clientKey, topic, e);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new MqttOperationException("publish", clientKey, topic, e);
        } catch (ExecutionException e) {
            throw new MqttOperationException("publish", clientKey, topic, e.getCause());
        }
    }

    /**
     * 使用指定客户端发布 UTF-8 文本消息。
     *
     * @param clientKey 客户端键
     * @param topic     发布主题
     * @param payload   消息正文
     * @param qos       QoS
     */
    public void publish(String clientKey, String topic, String payload, int qos) {
        if (payload == null) {
            throw new IllegalArgumentException("MQTT payload must not be null");
        }
        requireQos(qos);
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        message.setRetained(false);
        publish(clientKey, topic, message);
    }

    /**
     * 使用默认客户端发布 UTF-8 文本消息。
     *
     * @param topic   发布主题
     * @param payload 消息正文
     * @param qos     QoS
     */
    public void publish(String topic, String payload, int qos) {
        publish(DEFAULT_CLIENT_KEY, topic, payload, qos);
    }

    /**
     * 为指定客户端订阅消息处理器。
     *
     * @param clientKey 客户端键
     * @param handler   消息处理器
     */
    public synchronized void subscribe(String clientKey, MqttMessageHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("MQTT message handler must not be null");
        }
        MqttTopics.validateFilter(handler.topic());
        requireQos(handler.qos());
        MqttClientContext context = requireConnectedContext(clientKey);
        if (context.containsHandler(handler)) {
            return;
        }
        String wireFilter = wireFilter(handler);
        int currentQos = currentSubscriptionQos(context, wireFilter);
        int requiredQos = Math.max(currentQos, handler.qos());
        CopyOnWriteArrayList<MqttMessageHandler> registrations = handlerRegistrations
                .computeIfAbsent(clientKey, ignored -> new CopyOnWriteArrayList<>());
        if (registrations.stream().anyMatch(current -> current == handler)) {
            return;
        }
        registrations.add(handler);
        context.addHandler(handler);
        try {
            if (requiredQos > currentQos) {
                context.getClient().subscribe(new MqttSubscription(wireFilter, requiredQos));
            }
        } catch (MqttException e) {
            context.removeHandler(handler);
            registrations.removeIf(current -> current == handler);
            if (registrations.isEmpty()) {
                handlerRegistrations.remove(clientKey, registrations);
            }
            throw new MqttOperationException("subscribe", clientKey, handler.topic(), e);
        }
    }

    /**
     * 取消指定客户端的一个或多个订阅过滤器。
     *
     * @param clientKey 客户端键
     * @param filters   普通订阅过滤器
     */
    public synchronized void unsubscribe(String clientKey, String... filters) {
        if (filters == null || filters.length == 0) {
            throw new IllegalArgumentException("At least one MQTT filter is required");
        }
        MqttClientContext context = requireConnectedContext(clientKey);
        Set<String> wireFilters = new LinkedHashSet<>();
        for (String filter : filters) {
            MqttTopics.validateFilter(filter);
            List<MqttMessageHandler> registered = context.getHandlers(filter);
            if (registered.isEmpty()) {
                if (!hasTemporarySubscription(clientKey, filter)) {
                    wireFilters.add(filter);
                }
            } else {
                registered.stream().map(this::wireFilter)
                        .filter(wireFilter -> !wireFilter.equals(filter)
                                || !hasTemporarySubscription(clientKey, filter))
                        .forEach(wireFilters::add);
            }
        }
        if (!wireFilters.isEmpty()) {
            try {
                context.getClient().unsubscribe(wireFilters.toArray(String[]::new));
            } catch (MqttException e) {
                throw new MqttOperationException("unsubscribe", clientKey,
                        String.join(",", filters), e);
            }
        }
        for (String filter : filters) {
            context.removeHandlers(filter);
            CopyOnWriteArrayList<MqttMessageHandler> registrations = handlerRegistrations.get(clientKey);
            if (registrations != null) {
                registrations.removeIf(handler -> handler.topic().equals(filter));
                if (registrations.isEmpty()) {
                    handlerRegistrations.remove(clientKey, registrations);
                }
            }
        }
    }

    /**
     * 发布一次请求并同步等待关联响应。
     *
     * @param clientKey   客户端键
     * @param requestTopic 请求主题
     * @param replyFilter 回复主题过滤器
     * @param payload     UTF-8 请求正文
     * @param qos         请求与临时订阅 QoS
     * @param timeout     单次等待超时时间
     * @param extractor   请求和响应关联键提取器
     * @return 匹配的响应，超时为空
     */
    public Optional<MqttMessageContext> request(
            String clientKey, String requestTopic, String replyFilter, String payload,
            int qos, Duration timeout, MqttCorrelationExtractor extractor) {
        return requestWithRetry(clientKey, requestTopic, replyFilter, payload,
                qos, timeout, extractor, 1);
    }

    /**
     * 发布请求并在超时后重新发布，直至收到响应或用完尝试次数。
     *
     * @param clientKey    客户端键
     * @param requestTopic 请求主题
     * @param replyFilter  回复主题过滤器
     * @param payload      UTF-8 请求正文
     * @param qos          请求与临时订阅 QoS
     * @param timeout      每次尝试的等待超时时间
     * @param extractor    请求和响应关联键提取器
     * @param attempts     总发布次数，至少为一
     * @return 匹配的响应，所有尝试均超时时为空
     */
    public Optional<MqttMessageContext> requestWithRetry(
            String clientKey, String requestTopic, String replyFilter, String payload,
            int qos, Duration timeout, MqttCorrelationExtractor extractor, int attempts) {
        if (attempts < 1) {
            throw new IllegalArgumentException("MQTT request attempts must be at least one");
        }
        if (payload == null) {
            throw new IllegalArgumentException("MQTT payload must not be null");
        }
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must not be negative");
        }
        if (extractor == null) {
            throw new IllegalArgumentException("Correlation extractor must not be null");
        }
        MqttTopics.validateTopic(requestTopic);
        MqttTopics.validateFilter(replyFilter);
        requireQos(qos);
        MqttClientContext context = requireConnectedContext(clientKey);
        String correlationKey = extractor.extract(MqttCorrelationType.REQUEST, payload);
        if (correlationKey == null || correlationKey.isBlank()) {
            throw new IllegalArgumentException("Correlation key must not be blank");
        }

        boolean temporarySubscription = acquireTemporarySubscription(context, replyFilter, qos);
        try {
            for (int attempt = 0; attempt < attempts; attempt++) {
                context.getRequestContext().register(replyFilter, correlationKey, extractor);
                try {
                    publish(clientKey, requestTopic, payload, qos);
                    Optional<MqttMessageContext> response =
                            context.getRequestContext().await(correlationKey, timeout);
                    if (response.isPresent()) {
                        return response;
                    }
                } finally {
                    context.getRequestContext().cancel(replyFilter, correlationKey);
                }
            }
            return Optional.empty();
        } finally {
            if (temporarySubscription) {
                releaseTemporarySubscription(context, replyFilter);
            }
        }
    }

    /**
     * 关闭并移除指定客户端。
     *
     * @param clientKey 客户端键
     */
    public synchronized void close(String clientKey) {
        close(clientKey, true);
    }

    private void close(String clientKey, boolean removeHandlerRegistrations) {
        MqttClientContext context = clients.remove(clientKey);
        fingerprints.remove(clientKey);
        connectedHandlers.remove(clientKey);
        temporarySubscriptionCounters.remove(clientKey);
        temporarySubscriptionQos.remove(clientKey);
        if (removeHandlerRegistrations) {
            handlerRegistrations.remove(clientKey);
        }
        ReconnectTask reconnectTask = reconnectTasks.remove(clientKey);
        if (reconnectTask != null) {
            reconnectTask.cancel();
        }
        if (context == null) {
            return;
        }
        closeContext(context);
    }

    /**
     * 关闭全部受管理客户端。外部传入的线程池不会被关闭。
     */
    @Override
    public synchronized void close() {
        for (String clientKey : new ArrayList<>(clients.keySet())) {
            close(clientKey);
        }
    }

    int reconnectTaskCount() {
        return reconnectTasks.size();
    }

    MqttClientContext getContext(String clientKey) {
        return clients.get(clientKey);
    }

    ExecutorService getPublishExecutor() {
        return publishExecutor;
    }

    private MqttClientContext requireConnectedContext(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            throw new IllegalArgumentException("MQTT client key must not be blank");
        }
        MqttClientContext context = clients.get(clientKey);
        if (context == null || !context.getClient().isConnected()) {
            throw new IllegalStateException("MQTT client is not connected: " + clientKey);
        }
        return context;
    }

    private String wireFilter(MqttMessageHandler handler) {
        return handler.shareGroup() == null || handler.shareGroup().isBlank()
                ? handler.topic() : MqttTopics.shared(handler.shareGroup(), handler.topic());
    }

    private int currentSubscriptionQos(MqttClientContext context, String wireFilter) {
        int qos = context.getHandlers().stream()
                .filter(handler -> wireFilter(handler).equals(wireFilter))
                .mapToInt(MqttMessageHandler::qos)
                .max()
                .orElse(-1);
        Map<String, Integer> temporaryQos = temporarySubscriptionQos.get(context.getClientKey());
        if (temporaryQos != null) {
            qos = Math.max(qos, temporaryQos.getOrDefault(wireFilter, -1));
        }
        return qos;
    }

    private synchronized boolean acquireTemporarySubscription(
            MqttClientContext context, String filter, int qos) {
        if (!isCurrent(context) || context.isClosing()) {
            throw new IllegalStateException("MQTT client is no longer current: "
                    + context.getClientKey());
        }
        Map<String, AtomicInteger> clientCounters = temporarySubscriptionCounters
                .computeIfAbsent(context.getClientKey(), ignored -> new ConcurrentHashMap<>());
        AtomicInteger counter = clientCounters.get(filter);
        if (counter != null) {
            Map<String, Integer> clientQos = temporarySubscriptionQos
                    .computeIfAbsent(context.getClientKey(), ignored -> new ConcurrentHashMap<>());
            int currentQos = currentSubscriptionQos(context, filter);
            if (qos > currentQos) {
                try {
                    context.getClient().subscribe(new MqttSubscription(filter, qos));
                } catch (MqttException e) {
                    throw new MqttOperationException(
                            "subscribe", context.getClientKey(), filter, e);
                }
            }
            clientQos.merge(filter, qos, Math::max);
            counter.incrementAndGet();
            return true;
        }
        int currentQos = currentSubscriptionQos(context, filter);
        if (qos > currentQos) {
            try {
                context.getClient().subscribe(new MqttSubscription(filter, qos));
            } catch (MqttException e) {
                if (clientCounters.isEmpty()) {
                    temporarySubscriptionCounters.remove(context.getClientKey(), clientCounters);
                }
                throw new MqttOperationException("subscribe", context.getClientKey(), filter, e);
            }
        }
        clientCounters.put(filter, new AtomicInteger(1));
        temporarySubscriptionQos
                .computeIfAbsent(context.getClientKey(), ignored -> new ConcurrentHashMap<>())
                .put(filter, qos);
        return true;
    }

    private boolean hasTemporarySubscription(String clientKey, String filter) {
        Map<String, AtomicInteger> subscriptions = temporarySubscriptionCounters.get(clientKey);
        AtomicInteger counter = subscriptions == null ? null : subscriptions.get(filter);
        return counter != null && counter.get() > 0;
    }

    private synchronized void releaseTemporarySubscription(
            MqttClientContext context, String filter) {
        Map<String, AtomicInteger> clientCounters =
                temporarySubscriptionCounters.get(context.getClientKey());
        if (clientCounters == null) {
            return;
        }
        AtomicInteger counter = clientCounters.get(filter);
        if (counter == null || counter.decrementAndGet() > 0) {
            return;
        }
        clientCounters.remove(filter, counter);
        Map<String, Integer> clientQos = temporarySubscriptionQos.get(context.getClientKey());
        Integer releasedQos = null;
        if (clientQos != null) {
            releasedQos = clientQos.remove(filter);
            if (clientQos.isEmpty()) {
                temporarySubscriptionQos.remove(context.getClientKey(), clientQos);
            }
        }
        if (clientCounters.isEmpty()) {
            temporarySubscriptionCounters.remove(context.getClientKey(), clientCounters);
        }
        if (!isCurrent(context) || context.isClosing()) {
            return;
        }
        int longTermQos = context.getHandlers().stream()
                .filter(handler -> wireFilter(handler).equals(filter))
                .mapToInt(MqttMessageHandler::qos)
                .max()
                .orElse(-1);
        try {
            if (longTermQos >= 0) {
                if (releasedQos != null && releasedQos > longTermQos) {
                    context.getClient().subscribe(new MqttSubscription(filter, longTermQos));
                }
            } else {
                context.getClient().unsubscribe(filter);
            }
        } catch (MqttException e) {
            LOG.warn("Unable to restore MQTT subscription after temporary request, clientKey={}, filter={}",
                    context.getClientKey(), filter, e);
        }
    }

    private void createClient(String clientKey, MqttClientOptions options, String fingerprint) {
        MqttClientAdapter client;
        try {
            client = clientFactory.create(options);
        } catch (MqttException e) {
            throw new MqttOperationException("create client", clientKey, null, e);
        }
        MqttClientContext context = new MqttClientContext(
                clientKey, client, options, responseWaiter, handlerExecutor);
        List<MqttMessageHandler> registrations = handlerRegistrations.get(clientKey);
        if (registrations != null) {
            registrations.forEach(context::addHandler);
        }
        client.setCallback(new MqttClientCallback(context, this::connected, this::disconnected));
        clients.put(clientKey, context);
        fingerprints.put(clientKey, fingerprint);
        connectionExecutor.execute(() -> connect(context));
    }

    private void connect(MqttClientContext context) {
        if (!isCurrent(context) || context.isClosing()) {
            return;
        }
        try {
            context.getClient().connect(connectionOptions(context.getOptions()));
            if (!isCurrent(context) || context.isClosing()) {
                closeContext(context);
            }
        } catch (MqttException e) {
            if (!isCurrent(context) || context.isClosing()) {
                closeContext(context);
                return;
            }
            LOG.warn("MQTT connection failed, clientKey={}", context.getClientKey(), e);
            scheduleReconnect(context);
        }
    }

    private void connected(MqttClientContext context) {
        if (!isCurrent(context) || context.isClosing()) {
            return;
        }
        ReconnectTask reconnectTask = reconnectTasks.remove(context.getClientKey());
        if (reconnectTask != null) {
            reconnectTask.cancel();
        }
        restoreSubscriptions(context);
        BiConsumer<String, MqttClientOptions> handler = connectedHandlers.get(context.getClientKey());
        if (handler != null) {
            try {
                handler.accept(context.getClientKey(), context.getOptions());
            } catch (RuntimeException e) {
                LOG.error("MQTT connected callback failed, clientKey={}", context.getClientKey(), e);
                scheduleConnectedHandlerRetry(context, handler);
            }
        }
    }

    private void disconnected(MqttClientContext context) {
        scheduleReconnect(context);
    }

    private synchronized void restoreSubscriptions(MqttClientContext context) {
        Map<String, Integer> subscriptions = new java.util.LinkedHashMap<>();
        for (MqttMessageHandler handler : context.getHandlers()) {
            subscriptions.merge(wireFilter(handler), handler.qos(), Math::max);
        }
        Map<String, Integer> temporaryFilters =
                temporarySubscriptionQos.get(context.getClientKey());
        if (temporaryFilters != null) {
            temporaryFilters.forEach(
                    (filter, qos) -> subscriptions.merge(filter, qos, Math::max));
        }
        subscriptions.forEach((filter, qos) -> {
            try {
                context.getClient().subscribe(new MqttSubscription(filter, qos));
            } catch (MqttException e) {
                LOG.error("Unable to restore MQTT subscription, clientKey={}, filter={}",
                        context.getClientKey(), filter, e);
                scheduleSubscriptionRestoreRetry(context, filter);
            }
        });
    }

    private void scheduleConnectedHandlerRetry(
            MqttClientContext context,
            BiConsumer<String, MqttClientOptions> handler) {
        connectionExecutor.schedule(() -> retryConnectedHandler(context, handler),
                context.getOptions().getReconnectDelayMillis(), TimeUnit.MILLISECONDS);
    }

    private void retryConnectedHandler(
            MqttClientContext context,
            BiConsumer<String, MqttClientOptions> handler) {
        if (!isCurrent(context) || context.isClosing() || !context.getClient().isConnected()) {
            return;
        }
        try {
            handler.accept(context.getClientKey(), context.getOptions());
        } catch (RuntimeException e) {
            LOG.error("MQTT connected callback retry failed, clientKey={}",
                    context.getClientKey(), e);
        }
    }

    private void scheduleSubscriptionRestoreRetry(
            MqttClientContext context, String wireFilter) {
        connectionExecutor.schedule(() -> retrySubscriptionRestore(context, wireFilter),
                context.getOptions().getReconnectDelayMillis(), TimeUnit.MILLISECONDS);
    }

    private synchronized void retrySubscriptionRestore(
            MqttClientContext context, String wireFilter) {
        if (!isCurrent(context) || context.isClosing() || !context.getClient().isConnected()) {
            return;
        }
        int qos = currentSubscriptionQos(context, wireFilter);
        if (qos < 0) {
            return;
        }
        try {
            context.getClient().subscribe(new MqttSubscription(wireFilter, qos));
        } catch (MqttException e) {
            LOG.error("MQTT subscription restore retry failed, clientKey={}, filter={}",
                    context.getClientKey(), wireFilter, e);
        }
    }

    private synchronized void scheduleReconnect(MqttClientContext context) {
        if (!isCurrent(context) || context.isClosing() || !context.getOptions().isReconnectEnabled()) {
            return;
        }
        String clientKey = context.getClientKey();
        ReconnectTask currentTask = reconnectTasks.get(clientKey);
        if (currentTask != null && !currentTask.isDone()) {
            return;
        }
        ReconnectTask reconnectTask = new ReconnectTask(context);
        reconnectTasks.put(clientKey, reconnectTask);
        try {
            reconnectTask.setFuture(connectionExecutor.schedule(
                    () -> runReconnect(reconnectTask),
                    context.getOptions().getReconnectDelayMillis(), TimeUnit.MILLISECONDS));
        } catch (RuntimeException e) {
            reconnectTasks.remove(clientKey, reconnectTask);
            throw e;
        }
    }

    private void runReconnect(ReconnectTask reconnectTask) {
        synchronized (this) {
            if (!reconnectTasks.remove(
                    reconnectTask.context.getClientKey(), reconnectTask)) {
                return;
            }
        }
        connect(reconnectTask.context);
    }

    private void closeContext(MqttClientContext context) {
        context.markClosing();
        context.getRequestContext().cancelAll();
        try {
            if (context.getClient().isConnected()) {
                context.getClient().disconnect();
            }
        } catch (MqttException e) {
            LOG.warn("Unable to disconnect MQTT client, clientKey={}",
                    context.getClientKey(), e);
        }
        try {
            context.getClient().close();
        } catch (MqttException e) {
            LOG.warn("Unable to close MQTT client, clientKey={}",
                    context.getClientKey(), e);
        }
    }

    private boolean isCurrent(MqttClientContext context) {
        return clients.get(context.getClientKey()) == context;
    }

    private MqttConnectionOptions connectionOptions(MqttClientOptions options) {
        MqttConnectionOptions connectionOptions = new MqttConnectionOptions();
        if (options.getUsername() != null && !options.getUsername().isBlank()) {
            connectionOptions.setUserName(options.getUsername());
        }
        if (options.getPassword() != null) {
            connectionOptions.setPassword(options.getPassword().getBytes(StandardCharsets.UTF_8));
        }
        connectionOptions.setCleanStart(options.isCleanStart());
        connectionOptions.setKeepAliveInterval(options.getKeepAliveSeconds());
        connectionOptions.setConnectionTimeout(options.getConnectionTimeoutSeconds());
        connectionOptions.setAutomaticReconnect(false);
        MqttWillOptions will = options.getWill();
        if (will.isEnabled()) {
            MqttMessage willMessage = new MqttMessage(will.getPayload().getBytes(StandardCharsets.UTF_8));
            willMessage.setQos(will.getQos());
            willMessage.setRetained(will.isRetained());
            connectionOptions.setWill(will.getTopic(), willMessage);
        }
        return connectionOptions;
    }

    private void validateClient(String clientKey, MqttClientOptions options) {
        if (clientKey == null || clientKey.isBlank()) {
            throw new IllegalArgumentException("MQTT client key must not be blank");
        }
        if (options == null) {
            throw new IllegalArgumentException("MQTT client options must not be null");
        }
        if (!options.isEnabled()) {
            throw new IllegalArgumentException("MQTT refresh accepts enabled clients only: " + clientKey);
        }
        if (options.getBroker() == null || options.getBroker().isBlank()) {
            throw new IllegalArgumentException("MQTT broker must not be blank: " + clientKey);
        }
        if (options.getKeepAliveSeconds() <= 0 || options.getConnectionTimeoutSeconds() <= 0
                || options.getPublishTimeoutSeconds() <= 0 || options.getReconnectDelayMillis() <= 0) {
            throw new IllegalArgumentException("MQTT timeout and interval values must be positive: " + clientKey);
        }
        MqttWillOptions will = options.getWill();
        if (will.isEnabled()) {
            MqttTopics.validateTopic(will.getTopic());
            if (will.getPayload() == null) {
                throw new IllegalArgumentException("MQTT will payload must not be null: " + clientKey);
            }
            requireQos(will.getQos());
        }
    }

    static void requireQos(int qos) {
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("MQTT QoS must be between 0 and 2");
        }
    }

    private String fingerprint(MqttClientOptions options) {
        MqttWillOptions will = options.getWill();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateFingerprintField(digest, options.getBroker());
            updateFingerprintField(digest, options.resolveClientId());
            updateFingerprintField(digest, options.getUsername());
            updateFingerprintField(digest, options.getPassword());
            updateFingerprintField(digest, String.valueOf(options.isCleanStart()));
            updateFingerprintField(digest, String.valueOf(options.getKeepAliveSeconds()));
            updateFingerprintField(digest, String.valueOf(options.getConnectionTimeoutSeconds()));
            updateFingerprintField(digest, String.valueOf(options.getPublishTimeoutSeconds()));
            updateFingerprintField(digest, String.valueOf(options.isReconnectEnabled()));
            updateFingerprintField(digest, String.valueOf(options.getReconnectDelayMillis()));
            updateFingerprintField(digest, options.getPersistenceDirectory());
            updateFingerprintField(digest, String.valueOf(will.isEnabled()));
            updateFingerprintField(digest, will.getTopic());
            updateFingerprintField(digest, will.getPayload());
            updateFingerprintField(digest, String.valueOf(will.getQos()));
            updateFingerprintField(digest, String.valueOf(will.isRetained()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void updateFingerprintField(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        digest.update((byte) 1);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static final class ReconnectTask {
        private final MqttClientContext context;
        private volatile ScheduledFuture<?> future;

        private ReconnectTask(MqttClientContext context) {
            this.context = context;
        }

        private void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }

        private boolean isDone() {
            ScheduledFuture<?> current = future;
            return current != null && current.isDone();
        }

        private void cancel() {
            ScheduledFuture<?> current = future;
            if (current != null) {
                current.cancel(false);
            }
        }
    }
}

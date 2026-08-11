package com.ss.mqttv3.client;

import com.ss.mqttv3.config.MqttClientOptions;
import com.ss.mqttv3.handler.MqttMessageHandler;
import com.ss.mqttv3.topic.MqttTopics;
import com.ss.mqttv3.waiter.MqttRequestContext;
import com.ss.mqttv3.waiter.MqttResponseWaiter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单个 MQTT 客户端连接的内部运行上下文。
 */
final class MqttClientContext {
    private static final int HANDLER_CACHE_CAPACITY = 1_024;

    private final String clientKey;
    private final MqttClientAdapter client;
    private final MqttClientOptions options;
    private final MqttRequestContext requestContext;
    private final ExecutorService handlerExecutor;
    private final CopyOnWriteArrayList<MqttMessageHandler> handlers = new CopyOnWriteArrayList<>();
    private final Map<String, CachedHandlers> handlerCache = new ConcurrentHashMap<>();
    private final AtomicLong handlerVersion = new AtomicLong();
    private volatile boolean closing;

    MqttClientContext(String clientKey, MqttClientAdapter client, MqttClientOptions options,
                      MqttResponseWaiter responseWaiter, ExecutorService handlerExecutor) {
        this.clientKey = clientKey;
        this.client = client;
        this.options = options;
        this.requestContext = new MqttRequestContext(clientKey, responseWaiter);
        this.handlerExecutor = handlerExecutor;
    }

    String getClientKey() {
        return clientKey;
    }

    MqttClientAdapter getClient() {
        return client;
    }

    MqttClientOptions getOptions() {
        return options;
    }

    MqttRequestContext getRequestContext() {
        return requestContext;
    }

    ExecutorService getHandlerExecutor() {
        return handlerExecutor;
    }

    void addHandler(MqttMessageHandler handler) {
        boolean exists = handlers.stream().anyMatch(current -> current == handler);
        if (!exists) {
            handlers.add(handler);
            handlerVersion.incrementAndGet();
            handlerCache.clear();
        }
    }

    void removeHandler(MqttMessageHandler handler) {
        if (handlers.removeIf(current -> current == handler)) {
            handlerVersion.incrementAndGet();
            handlerCache.clear();
        }
    }

    List<MqttMessageHandler> removeHandlers(String filter) {
        List<MqttMessageHandler> removed = getHandlers(filter);
        if (handlers.removeIf(handler -> handler.topic().equals(filter))) {
            handlerVersion.incrementAndGet();
            handlerCache.clear();
        }
        return removed;
    }

    List<MqttMessageHandler> getMatchingHandlers(String topic) {
        long lookupVersion = handlerVersion.get();
        CachedHandlers cached = handlerCache.get(topic);
        if (cached != null && cached.version() == lookupVersion
                && handlerVersion.get() == lookupVersion) {
            return cached.handlers();
        }
        List<MqttMessageHandler> matches = new ArrayList<>();
        for (MqttMessageHandler handler : handlers) {
            if (MqttTopics.matches(handler.topic(), topic)) {
                matches.add(handler);
            }
        }
        List<MqttMessageHandler> result = matches.isEmpty()
                ? List.of() : Collections.unmodifiableList(matches);
        if (handlerVersion.get() == lookupVersion) {
            if (handlerCache.size() >= HANDLER_CACHE_CAPACITY) {
                handlerCache.clear();
            }
            handlerCache.put(topic, new CachedHandlers(lookupVersion, result));
        }
        return result;
    }

    List<MqttMessageHandler> getHandlers() {
        return List.copyOf(handlers);
    }

    List<MqttMessageHandler> getHandlers(String filter) {
        return handlers.stream().filter(handler -> handler.topic().equals(filter)).toList();
    }

    boolean containsHandler(MqttMessageHandler handler) {
        return handlers.stream().anyMatch(current -> current == handler);
    }

    boolean hasDirectFilter(String filter) {
        return handlers.stream().anyMatch(handler -> handler.topic().equals(filter)
                && (handler.shareGroup() == null || handler.shareGroup().isBlank()));
    }

    void markClosing() {
        closing = true;
    }

    boolean isClosing() {
        return closing;
    }

    private record CachedHandlers(long version, List<MqttMessageHandler> handlers) {
    }
}

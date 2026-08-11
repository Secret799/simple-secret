package com.ss.mqttv3.waiter;

import com.ss.mqttv3.message.MqttMessageContext;
import com.ss.mqttv3.topic.MqttTopics;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个 MQTT 客户端的请求响应关联上下文。
 */
public final class MqttRequestContext {
    private final String clientKey;
    private final MqttResponseWaiter waiter;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, MqttCorrelationExtractor>> registrations =
            new ConcurrentHashMap<>();

    /**
     * 创建请求响应上下文。
     *
     * @param clientKey 客户端键
     * @param waiter    响应等待器
     */
    public MqttRequestContext(String clientKey, MqttResponseWaiter waiter) {
        if (clientKey == null || clientKey.isBlank()) {
            throw new IllegalArgumentException("Client key must not be blank");
        }
        if (waiter == null) {
            throw new IllegalArgumentException("Response waiter must not be null");
        }
        this.clientKey = clientKey;
        this.waiter = waiter;
    }

    /**
     * 注册回复主题和关联键。
     *
     * @param filter         回复主题过滤器
     * @param correlationKey 请求关联键
     * @param extractor      关联键提取器
     */
    public void register(String filter, String correlationKey, MqttCorrelationExtractor extractor) {
        MqttTopics.validateFilter(filter);
        requireCorrelationKey(correlationKey);
        if (extractor == null) {
            throw new IllegalArgumentException("Correlation extractor must not be null");
        }
        waiter.register(waitKey(correlationKey));
        registrations.computeIfAbsent(filter, ignored -> new ConcurrentHashMap<>())
                .put(correlationKey, extractor);
    }

    /**
     * 尝试用入站消息完成一个等待项。
     *
     * @param message 入站消息
     * @return 完成等待项时返回 {@code true}
     */
    public boolean handle(MqttMessageContext message) {
        if (message == null || !clientKey.equals(message.getClientKey())) {
            return false;
        }
        for (Map.Entry<String, ConcurrentHashMap<String, MqttCorrelationExtractor>> filterEntry
                : registrations.entrySet()) {
            String filter = filterEntry.getKey();
            if (!MqttTopics.matches(filter, message.getTopic())) {
                continue;
            }
            ConcurrentHashMap<String, MqttCorrelationExtractor> correlations = filterEntry.getValue();
            for (Map.Entry<String, MqttCorrelationExtractor> correlationEntry : correlations.entrySet()) {
                String responseKey;
                try {
                    responseKey = correlationEntry.getValue()
                            .extract(MqttCorrelationType.RESPONSE, message.getPayloadAsString());
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (responseKey == null || responseKey.isBlank()
                        || !correlationEntry.getKey().equals(responseKey)) {
                    continue;
                }
                MqttMessageContext response = new MqttMessageContext(
                        message.getClientKey(), message.getClientId(), message.getShareGroup(),
                        filter, message.getTopic(), message.getMessage());
                if (waiter.complete(waitKey(responseKey), response)) {
                    correlations.remove(responseKey, correlationEntry.getValue());
                    if (correlations.isEmpty()) {
                        registrations.remove(filter, correlations);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 等待指定关联键的响应。
     *
     * @param correlationKey 关联键
     * @param timeout        超时时间
     * @return 响应消息，超时为空
     */
    public Optional<MqttMessageContext> await(String correlationKey, Duration timeout) {
        requireCorrelationKey(correlationKey);
        return waiter.await(waitKey(correlationKey), timeout);
    }

    /**
     * 取消一个回复注册。
     *
     * @param filter         回复主题过滤器
     * @param correlationKey 关联键
     */
    public void cancel(String filter, String correlationKey) {
        requireCorrelationKey(correlationKey);
        waiter.cancel(waitKey(correlationKey));
        ConcurrentHashMap<String, MqttCorrelationExtractor> correlations = registrations.get(filter);
        if (correlations != null) {
            correlations.remove(correlationKey);
            if (correlations.isEmpty()) {
                registrations.remove(filter, correlations);
            }
        }
    }

    /**
     * 取消当前客户端的全部挂起请求。
     */
    public void cancelAll() {
        registrations.forEach((filter, correlations) ->
                correlations.keySet().forEach(correlationKey -> waiter.cancel(waitKey(correlationKey))));
        registrations.clear();
    }

    int registrationCount() {
        return registrations.values().stream().mapToInt(Map::size).sum();
    }

    private String waitKey(String correlationKey) {
        return clientKey.length() + ":" + clientKey + correlationKey;
    }

    private static void requireCorrelationKey(String correlationKey) {
        if (correlationKey == null || correlationKey.isBlank()) {
            throw new IllegalArgumentException("Correlation key must not be blank");
        }
    }
}

package com.ss.nats.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code simple-secret.nats} 下的 NATS 自动配置属性。
 */
@ConfigurationProperties("simple-secret.nats")
public class NatsProperties {
    /**
     * 是否启用。
     */
    private boolean enabled = true;
    /**
     * 消息处理核心线程数。
     */
    private int handlerCoreSize = 4;
    /**
     * 消息处理最大线程数。
     */
    private int handlerMaxSize = 16;
    /**
     * 消息处理队列容量。
     */
    private int handlerQueueCapacity = 1024;
    /**
     * 消息发布核心线程数。
     */
    private int publishCoreSize = 2;
    /**
     * 消息发布最大线程数。
     */
    private int publishMaxSize = 8;
    /**
     * 消息发布队列容量。
     */
    private int publishQueueCapacity = 1024;
    /**
     * 以客户端键分组的配置。
     */
    private Map<String, NatsClientOptions> clients = new LinkedHashMap<>();

    /** @return 是否启用 NATS 自动配置 */
    public boolean isEnabled() { return enabled; }
    /** @param enabled 是否启用 NATS 自动配置 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** @return 消息处理核心线程数 */
    public int getHandlerCoreSize() { return handlerCoreSize; }
    /** @param value 消息处理核心线程数 */
    public void setHandlerCoreSize(int value) { handlerCoreSize = value; }
    /** @return 消息处理最大线程数 */
    public int getHandlerMaxSize() { return handlerMaxSize; }
    /** @param value 消息处理最大线程数 */
    public void setHandlerMaxSize(int value) { handlerMaxSize = value; }
    /** @return 消息处理队列容量 */
    public int getHandlerQueueCapacity() { return handlerQueueCapacity; }
    /** @param value 消息处理队列容量 */
    public void setHandlerQueueCapacity(int value) { handlerQueueCapacity = value; }
    /** @return 发布核心线程数 */
    public int getPublishCoreSize() { return publishCoreSize; }
    /** @param value 发布核心线程数 */
    public void setPublishCoreSize(int value) { publishCoreSize = value; }
    /** @return 发布最大线程数 */
    public int getPublishMaxSize() { return publishMaxSize; }
    /** @param value 发布最大线程数 */
    public void setPublishMaxSize(int value) { publishMaxSize = value; }
    /** @return 发布队列容量 */
    public int getPublishQueueCapacity() { return publishQueueCapacity; }
    /** @param value 发布队列容量 */
    public void setPublishQueueCapacity(int value) { publishQueueCapacity = value; }
    /** @return 以 clientKey 为键的客户端配置 */
    public Map<String, NatsClientOptions> getClients() { return clients; }
    /** @param value 以 clientKey 为键的客户端配置 */
    public void setClients(Map<String, NatsClientOptions> value) {
        clients = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    /** 校验线程池配置。 */
    public void validate() {
        requirePool(handlerCoreSize, handlerMaxSize, handlerQueueCapacity, "handler");
        requirePool(publishCoreSize, publishMaxSize, publishQueueCapacity, "publish");
    }

    private static void requirePool(int core, int max, int capacity, String name) {
        if (core <= 0 || max < core || capacity <= 0) {
            throw new IllegalArgumentException(name + " executor requires core > 0, max >= core and capacity > 0");
        }
    }
}

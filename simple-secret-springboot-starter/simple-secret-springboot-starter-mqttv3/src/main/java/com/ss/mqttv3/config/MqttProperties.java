package com.ss.mqttv3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code simple-secret.mqttv3} 下的 MQTT v3 自动配置属性。
 */
@ConfigurationProperties("simple-secret.mqttv3")
public class MqttProperties {
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
     * 连接调度核心线程数。
     */
    private int connectionCoreSize = 2;
    /**
     * 以客户端键分组的配置。
     */
    private Map<String, MqttClientOptions> clients = new LinkedHashMap<>();

    /** @return 是否启用 MQTT 自动配置 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用 MQTT 自动配置 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 消息处理线程池核心线程数 */
    public int getHandlerCoreSize() {
        return handlerCoreSize;
    }

    /** @param handlerCoreSize 消息处理线程池核心线程数 */
    public void setHandlerCoreSize(int handlerCoreSize) {
        this.handlerCoreSize = handlerCoreSize;
    }

    /** @return 消息处理线程池最大线程数 */
    public int getHandlerMaxSize() {
        return handlerMaxSize;
    }

    /** @param handlerMaxSize 消息处理线程池最大线程数 */
    public void setHandlerMaxSize(int handlerMaxSize) {
        this.handlerMaxSize = handlerMaxSize;
    }

    /** @return 消息处理线程池队列容量 */
    public int getHandlerQueueCapacity() {
        return handlerQueueCapacity;
    }

    /** @param handlerQueueCapacity 消息处理线程池队列容量 */
    public void setHandlerQueueCapacity(int handlerQueueCapacity) {
        this.handlerQueueCapacity = handlerQueueCapacity;
    }

    /** @return 发布线程池核心线程数 */
    public int getPublishCoreSize() {
        return publishCoreSize;
    }

    /** @param publishCoreSize 发布线程池核心线程数 */
    public void setPublishCoreSize(int publishCoreSize) {
        this.publishCoreSize = publishCoreSize;
    }

    /** @return 发布线程池最大线程数 */
    public int getPublishMaxSize() {
        return publishMaxSize;
    }

    /** @param publishMaxSize 发布线程池最大线程数 */
    public void setPublishMaxSize(int publishMaxSize) {
        this.publishMaxSize = publishMaxSize;
    }

    /** @return 发布线程池队列容量 */
    public int getPublishQueueCapacity() {
        return publishQueueCapacity;
    }

    /** @param publishQueueCapacity 发布线程池队列容量 */
    public void setPublishQueueCapacity(int publishQueueCapacity) {
        this.publishQueueCapacity = publishQueueCapacity;
    }

    /** @return 连接调度线程池核心线程数 */
    public int getConnectionCoreSize() {
        return connectionCoreSize;
    }

    /** @param connectionCoreSize 连接调度线程池核心线程数 */
    public void setConnectionCoreSize(int connectionCoreSize) {
        this.connectionCoreSize = connectionCoreSize;
    }

    /** @return 以 clientKey 为键的客户端配置 */
    public Map<String, MqttClientOptions> getClients() {
        return clients;
    }

    /** @param clients 以 clientKey 为键的客户端配置 */
    public void setClients(Map<String, MqttClientOptions> clients) {
        this.clients = clients == null ? new LinkedHashMap<>() : clients;
    }
}

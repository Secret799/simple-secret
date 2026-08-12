package com.ss.mqttv5.config;

import java.util.UUID;

/**
 * 单个 MQTT v5 客户端的连接与操作配置。
 */
public class MqttClientOptions {
    /**
     * 是否启用。
     */
    private boolean enabled;
    /**
     * Broker 地址。
     */
    private String broker;
    /**
     * 客户端 ID。
     */
    private String clientId;
    /**
     * 自动生成的客户端 ID。
     */
    private String generatedClientId;
    /**
     * 用户名。
     */
    private String username;
    /**
     * 密码。
     */
    private String password;
    /**
     * 是否使用 MQTT 5 清洁启动。
     */
    private boolean cleanStart = true;
    /**
     * 连接保活间隔，单位秒。
     */
    private int keepAliveSeconds = 30;
    /**
     * 连接超时时间，单位秒。
     */
    private int connectionTimeoutSeconds = 10;
    /**
     * 发布超时时间，单位秒。
     */
    private int publishTimeoutSeconds = 10;
    /**
     * 是否启用自动重连。
     */
    private boolean reconnectEnabled = true;
    /**
     * 重连延迟，单位毫秒。
     */
    private long reconnectDelayMillis = 1_000L;
    /**
     * 客户端持久化目录。
     */
    private String persistenceDirectory;
    /**
     * 遗嘱消息配置。
     */
    private MqttWillOptions will = new MqttWillOptions();

    /** @return 是否启用客户端 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用客户端 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return broker URI */
    public String getBroker() {
        return broker;
    }

    /** @param broker broker URI */
    public void setBroker(String broker) {
        this.broker = broker;
    }

    /** @return 显式配置的客户端 ID */
    public String getClientId() {
        return clientId;
    }

    /**
     * 设置客户端 ID，并重置此前生成的临时 ID。
     *
     * @param clientId 客户端 ID
     */
    public synchronized void setClientId(String clientId) {
        this.clientId = clientId;
        this.generatedClientId = null;
    }

    /**
     * 返回可用于连接的稳定客户端 ID。
     *
     * @return 显式 ID，或为当前配置实例生成一次的 UUID
     */
    public synchronized String resolveClientId() {
        if (clientId != null && !clientId.isBlank()) {
            return clientId;
        }
        if (generatedClientId == null) {
            generatedClientId = UUID.randomUUID().toString();
        }
        return generatedClientId;
    }

    /** @return 用户名 */
    public String getUsername() {
        return username;
    }

    /** @param username 用户名 */
    public void setUsername(String username) {
        this.username = username;
    }

    /** @return 密码 */
    public String getPassword() {
        return password;
    }

    /** @param password 密码 */
    public void setPassword(String password) {
        this.password = password;
    }

    /** @return 是否使用 clean start */
    public boolean isCleanStart() {
        return cleanStart;
    }

    /** @param cleanStart 是否使用 clean start */
    public void setCleanStart(boolean cleanStart) {
        this.cleanStart = cleanStart;
    }

    /** @return keep alive 秒数 */
    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    /** @param keepAliveSeconds keep alive 秒数 */
    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    /** @return 连接超时秒数 */
    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    /** @param connectionTimeoutSeconds 连接超时秒数 */
    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    /** @return 发布超时秒数 */
    public int getPublishTimeoutSeconds() {
        return publishTimeoutSeconds;
    }

    /** @param publishTimeoutSeconds 发布超时秒数 */
    public void setPublishTimeoutSeconds(int publishTimeoutSeconds) {
        this.publishTimeoutSeconds = publishTimeoutSeconds;
    }

    /** @return 是否启用断线重连 */
    public boolean isReconnectEnabled() {
        return reconnectEnabled;
    }

    /** @param reconnectEnabled 是否启用断线重连 */
    public void setReconnectEnabled(boolean reconnectEnabled) {
        this.reconnectEnabled = reconnectEnabled;
    }

    /** @return 重连间隔毫秒数 */
    public long getReconnectDelayMillis() {
        return reconnectDelayMillis;
    }

    /** @param reconnectDelayMillis 重连间隔毫秒数 */
    public void setReconnectDelayMillis(long reconnectDelayMillis) {
        this.reconnectDelayMillis = reconnectDelayMillis;
    }

    /** @return 文件持久化目录，空值表示内存持久化 */
    public String getPersistenceDirectory() {
        return persistenceDirectory;
    }

    /** @param persistenceDirectory 文件持久化目录 */
    public void setPersistenceDirectory(String persistenceDirectory) {
        this.persistenceDirectory = persistenceDirectory;
    }

    /** @return 遗嘱消息配置 */
    public MqttWillOptions getWill() {
        return will;
    }

    /** @param will 遗嘱消息配置；空值重置为禁用配置 */
    public void setWill(MqttWillOptions will) {
        this.will = will == null ? new MqttWillOptions() : will;
    }
}

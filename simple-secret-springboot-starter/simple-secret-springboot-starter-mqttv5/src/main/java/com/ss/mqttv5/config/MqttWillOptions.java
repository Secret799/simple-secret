package com.ss.mqttv5.config;

/**
 * MQTT 遗嘱消息配置。
 */
public class MqttWillOptions {
    /**
     * 是否启用。
     */
    private boolean enabled;
    /**
     * 消息主题。
     */
    private String topic;
    /**
     * 消息负载。
     */
    private String payload;
    /**
     * 消息 QoS。
     */
    private int qos;
    /**
     * 是否保留消息。
     */
    private boolean retained;

    /**
     * 返回是否启用遗嘱消息。
     *
     * @return 启用状态
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用遗嘱消息。
     *
     * @param enabled 启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回遗嘱主题。
     *
     * @return 遗嘱主题
     */
    public String getTopic() {
        return topic;
    }

    /**
     * 设置遗嘱主题。
     *
     * @param topic 遗嘱主题
     */
    public void setTopic(String topic) {
        this.topic = topic;
    }

    /**
     * 返回遗嘱消息正文。
     *
     * @return 遗嘱消息正文
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 设置遗嘱消息正文。
     *
     * @param payload 遗嘱消息正文
     */
    public void setPayload(String payload) {
        this.payload = payload;
    }

    /**
     * 返回遗嘱消息 QoS。
     *
     * @return QoS
     */
    public int getQos() {
        return qos;
    }

    /**
     * 设置遗嘱消息 QoS。
     *
     * @param qos QoS
     */
    public void setQos(int qos) {
        this.qos = qos;
    }

    /**
     * 返回遗嘱消息是否保留。
     *
     * @return retained 状态
     */
    public boolean isRetained() {
        return retained;
    }

    /**
     * 设置遗嘱消息是否保留。
     *
     * @param retained retained 状态
     */
    public void setRetained(boolean retained) {
        this.retained = retained;
    }
}

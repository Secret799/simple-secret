package com.ss.mqttv5.waiter;

/**
 * MQTT 关联键提取时的消息方向。
 */
public enum MqttCorrelationType {
    /** 请求消息。 */
    REQUEST,
    /** 响应消息。 */
    RESPONSE
}

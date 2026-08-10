package com.ss.mqttv5.waiter;

/**
 * 从 MQTT 请求或响应正文中提取关联键。
 */
@FunctionalInterface
public interface MqttCorrelationExtractor {

    /**
     * 提取关联键。
     *
     * @param type    消息方向
     * @param payload UTF-8 消息正文
     * @return 非空关联键
     */
    String extract(MqttCorrelationType type, String payload);
}

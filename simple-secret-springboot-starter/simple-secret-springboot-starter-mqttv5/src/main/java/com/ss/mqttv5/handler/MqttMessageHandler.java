package com.ss.mqttv5.handler;

import com.ss.mqttv5.message.MqttMessageContext;

/**
 * MQTT 入站消息处理器。
 */
public interface MqttMessageHandler {

    /**
     * 返回处理器所属客户端键。
     *
     * @return 客户端键，默认 {@code default}
     */
    default String clientKey() {
        return "default";
    }

    /**
     * 返回订阅过滤器。
     *
     * @return MQTT topic filter
     */
    String topic();

    /**
     * 返回订阅 QoS。
     *
     * @return QoS，默认为 0
     */
    default int qos() {
        return 0;
    }

    /**
     * 返回共享订阅组名称。
     *
     * @return 空字符串表示普通订阅
     */
    default String shareGroup() {
        return "";
    }

    /**
     * 处理入站消息。
     *
     * @param message 消息上下文
     */
    void handle(MqttMessageContext message);
}

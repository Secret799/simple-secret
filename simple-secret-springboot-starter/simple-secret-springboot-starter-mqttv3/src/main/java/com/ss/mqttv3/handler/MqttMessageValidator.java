package com.ss.mqttv3.handler;

import com.ss.mqttv3.message.MqttMessageContext;

/**
 * MQTT 消息处理前的可选校验契约。
 */
public interface MqttMessageValidator {

    /**
     * 判断消息是否允许进入处理器。
     *
     * @param message 消息上下文
     * @return 允许处理时返回 {@code true}
     */
    default boolean validate(MqttMessageContext message) {
        return true;
    }
}

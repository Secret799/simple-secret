package com.ss.mqttv5.exception;

import java.io.Serial;

/**
 * MQTT 协议操作失败时抛出的安全运行时异常。
 */
public class MqttOperationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建 MQTT 操作异常。
     *
     * @param operation 操作名称
     * @param clientKey 客户端键
     * @param topic     相关主题，可为空
     * @param cause     底层异常
     */
    public MqttOperationException(String operation, String clientKey, String topic, Throwable cause) {
        super("MQTT " + operation + " failed, clientKey=" + clientKey
                + (topic == null ? "" : ", topic=" + topic), cause);
    }
}

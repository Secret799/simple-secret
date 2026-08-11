package com.ss.mqttv3.waiter;

import com.ss.mqttv3.message.MqttMessageContext;

import java.time.Duration;
import java.util.Optional;

/**
 * MQTT 请求响应同步等待器。
 */
public interface MqttResponseWaiter {

    /**
     * 注册等待键。
     *
     * @param waitKey 等待键
     */
    void register(String waitKey);

    /**
     * 等待响应消息。
     *
     * @param waitKey 等待键
     * @param timeout 超时时间
     * @return 响应消息，超时或取消时为空
     */
    Optional<MqttMessageContext> await(String waitKey, Duration timeout);

    /**
     * 完成等待中的响应。
     *
     * @param waitKey 等待键
     * @param message 响应消息
     * @return 找到等待项且完成成功时返回 {@code true}
     */
    boolean complete(String waitKey, MqttMessageContext message);

    /**
     * 取消等待。
     *
     * @param waitKey 等待键
     */
    void cancel(String waitKey);
}

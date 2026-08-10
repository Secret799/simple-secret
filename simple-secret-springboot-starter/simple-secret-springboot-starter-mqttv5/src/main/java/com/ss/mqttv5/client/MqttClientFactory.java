package com.ss.mqttv5.client;

import com.ss.mqttv5.config.MqttClientOptions;
import org.eclipse.paho.mqttv5.common.MqttException;

/**
 * 创建底层 MQTT 客户端适配器。
 */
@FunctionalInterface
interface MqttClientFactory {
    MqttClientAdapter create(MqttClientOptions options) throws MqttException;
}

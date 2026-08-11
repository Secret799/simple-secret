package com.ss.mqttv3.client;

import com.ss.mqttv3.config.MqttClientOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

/**
 * 创建底层 MQTT 客户端适配器。
 */
@FunctionalInterface
interface MqttClientFactory {
    MqttClientAdapter create(MqttClientOptions options) throws MqttException;
}

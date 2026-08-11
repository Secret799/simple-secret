package com.ss.mqttv3.client;

import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * 核心客户端与 Paho 实现之间的最小适配边界。
 */
interface MqttClientAdapter {
    void setCallback(MqttCallbackExtended callback);

    void connect(MqttConnectOptions options) throws MqttException;

    boolean isConnected();

    String getClientId();

    void publish(String topic, MqttMessage message) throws MqttException;

    void subscribe(String filter, int qos) throws MqttException;

    void unsubscribe(String... filters) throws MqttException;

    void disconnect() throws MqttException;

    void close() throws MqttException;
}

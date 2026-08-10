package com.ss.mqttv5.client;

import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;

/**
 * 核心客户端与 Paho 实现之间的最小适配边界。
 */
interface MqttClientAdapter {
    void setCallback(MqttCallback callback);

    void connect(MqttConnectionOptions options) throws MqttException;

    boolean isConnected();

    String getClientId();

    void publish(String topic, MqttMessage message) throws MqttException;

    void subscribe(MqttSubscription... subscriptions) throws MqttException;

    void unsubscribe(String... filters) throws MqttException;

    void disconnect() throws MqttException;

    void close() throws MqttException;
}

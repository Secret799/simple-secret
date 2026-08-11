package com.ss.mqttv3.client;

import com.ss.mqttv3.config.MqttClientOptions;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * 基于 Eclipse Paho 同步客户端的适配实现。
 */
final class PahoMqttClientAdapter implements MqttClientAdapter {
    private final MqttClient client;

    PahoMqttClientAdapter(MqttClientOptions options) throws MqttException {
        MqttClientPersistence persistence = options.getPersistenceDirectory() == null
                || options.getPersistenceDirectory().isBlank()
                ? new MemoryPersistence()
                : new MqttDefaultFilePersistence(options.getPersistenceDirectory());
        this.client = new MqttClient(options.getBroker(), options.resolveClientId(), persistence);
    }

    @Override
    public void setCallback(MqttCallbackExtended callback) {
        client.setCallback(callback);
    }

    @Override
    public void connect(MqttConnectOptions options) throws MqttException {
        IMqttToken token = client.connectWithResult(options);
        token.waitForCompletion();
    }

    @Override
    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public String getClientId() {
        return client.getClientId();
    }

    @Override
    public void publish(String topic, MqttMessage message) throws MqttException {
        client.publish(topic, message);
    }

    @Override
    public void subscribe(String filter, int qos) throws MqttException {
        client.subscribe(filter, qos);
    }

    @Override
    public void unsubscribe(String... filters) throws MqttException {
        client.unsubscribe(filters);
    }

    @Override
    public void disconnect() throws MqttException {
        client.disconnect();
    }

    @Override
    public void close() throws MqttException {
        client.close();
    }
}

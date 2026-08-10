package com.ss.mqttv5.client;

import com.ss.mqttv5.config.MqttClientOptions;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttClientPersistence;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.client.persist.MqttDefaultFilePersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;

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
    public void setCallback(MqttCallback callback) {
        client.setCallback(callback);
    }

    @Override
    public void connect(MqttConnectionOptions options) throws MqttException {
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
    public void subscribe(MqttSubscription... subscriptions) throws MqttException {
        client.subscribe(subscriptions);
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

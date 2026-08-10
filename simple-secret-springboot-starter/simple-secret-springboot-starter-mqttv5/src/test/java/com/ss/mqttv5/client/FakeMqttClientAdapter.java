package com.ss.mqttv5.client;

import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

final class FakeMqttClientAdapter implements MqttClientAdapter {
    final String clientId;
    final List<MqttSubscription> subscriptions = new CopyOnWriteArrayList<>();
    final List<String> unsubscriptions = new CopyOnWriteArrayList<>();
    final List<String> publishedTopics = new CopyOnWriteArrayList<>();
    final List<MqttMessage> publishedMessages = new CopyOnWriteArrayList<>();
    final Set<String> activeSubscriptions = ConcurrentHashMap.newKeySet();
    MqttCallback callback;
    MqttConnectionOptions connectionOptions;
    boolean connected;
    boolean closed;
    int connectCount;
    int disconnectCount;
    MqttException connectFailure;
    MqttException publishFailure;
    MqttException subscribeFailure;
    int subscribeFailuresRemaining;
    MqttException unsubscribeFailure;
    BiConsumer<String, MqttMessage> publishHook;
    Runnable subscribeHook;
    Runnable unsubscribeHook;
    Runnable connectHook;
    Runnable connectFinishedHook;

    FakeMqttClientAdapter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public void setCallback(MqttCallback callback) {
        this.callback = callback;
    }

    @Override
    public void connect(MqttConnectionOptions options) throws MqttException {
        connectCount++;
        connectionOptions = options;
        if (connectFailure != null) {
            throw connectFailure;
        }
        if (connectHook != null) {
            connectHook.run();
        }
        connected = true;
        if (callback != null) {
            callback.connectComplete(connectCount > 1, "tcp://test");
        }
        if (connectFinishedHook != null) {
            connectFinishedHook.run();
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public String getClientId() {
        return clientId;
    }

    @Override
    public void publish(String topic, MqttMessage message) throws MqttException {
        if (publishFailure != null) {
            throw publishFailure;
        }
        publishedTopics.add(topic);
        publishedMessages.add(message);
        if (publishHook != null) {
            publishHook.accept(topic, message);
        }
    }

    @Override
    public void subscribe(MqttSubscription... subscriptions) throws MqttException {
        if (subscribeFailuresRemaining > 0) {
            subscribeFailuresRemaining--;
            throw new MqttException(8);
        }
        if (subscribeFailure != null) {
            throw subscribeFailure;
        }
        this.subscriptions.addAll(Arrays.asList(subscriptions));
        Arrays.stream(subscriptions).map(MqttSubscription::getTopic)
                .forEach(activeSubscriptions::add);
        if (subscribeHook != null) {
            subscribeHook.run();
        }
    }

    @Override
    public void unsubscribe(String... filters) throws MqttException {
        if (unsubscribeFailure != null) {
            throw unsubscribeFailure;
        }
        if (unsubscribeHook != null) {
            unsubscribeHook.run();
        }
        unsubscriptions.addAll(Arrays.asList(filters));
        activeSubscriptions.removeAll(Arrays.asList(filters));
    }

    @Override
    public void disconnect() {
        disconnectCount++;
        connected = false;
    }

    @Override
    public void close() {
        closed = true;
        connected = false;
    }

    void arrive(String topic, MqttMessage message) throws Exception {
        callback.messageArrived(topic, message);
    }

    void disconnectUnexpectedly() {
        connected = false;
        activeSubscriptions.clear();
        callback.disconnected(new MqttDisconnectResponse(new MqttException(0)));
    }
}

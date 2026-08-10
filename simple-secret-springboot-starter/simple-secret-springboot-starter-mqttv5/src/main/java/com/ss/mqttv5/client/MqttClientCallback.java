package com.ss.mqttv5.client;

import com.ss.mqttv5.handler.MqttMessageHandler;
import com.ss.mqttv5.handler.MqttMessageValidator;
import com.ss.mqttv5.message.MqttMessageContext;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * 将 Paho 回调转换为客户端生命周期和消息处理事件。
 */
final class MqttClientCallback implements MqttCallback {
    private static final Logger LOG = LoggerFactory.getLogger(MqttClientCallback.class);

    private final MqttClientContext context;
    private final Consumer<MqttClientContext> connectedHandler;
    private final Consumer<MqttClientContext> disconnectedHandler;

    MqttClientCallback(MqttClientContext context,
                       Consumer<MqttClientContext> connectedHandler,
                       Consumer<MqttClientContext> disconnectedHandler) {
        this.context = context;
        this.connectedHandler = connectedHandler;
        this.disconnectedHandler = disconnectedHandler;
    }

    @Override
    public void disconnected(MqttDisconnectResponse disconnectResponse) {
        int reasonCode = disconnectResponse == null ? -1 : disconnectResponse.getReturnCode();
        LOG.warn("MQTT client disconnected, clientKey={}, reasonCode={}",
                context.getClientKey(), reasonCode);
        disconnectedHandler.accept(context);
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
        LOG.error("MQTT client error, clientKey={}", context.getClientKey(), exception);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        MqttMessageContext snapshot = new MqttMessageContext(
                context.getClientKey(), context.getClient().getClientId(), "", topic, topic, message);
        try {
            context.getHandlerExecutor().execute(() -> dispatch(topic, snapshot));
        } catch (RuntimeException e) {
            LOG.error("MQTT message dispatch rejected, clientKey={}, topic={}",
                    context.getClientKey(), topic, e);
        }
    }

    private void dispatch(String topic, MqttMessageContext snapshot) {
        try {
            context.getRequestContext().handle(snapshot);
        } catch (RuntimeException e) {
            LOG.error("MQTT response correlation failed, clientKey={}, topic={}",
                    context.getClientKey(), topic, e);
        }

        List<MqttMessageHandler> handlers = context.getMatchingHandlers(topic);
        for (MqttMessageHandler handler : handlers) {
            MqttMessageContext handlerMessage = new MqttMessageContext(
                    context.getClientKey(), context.getClient().getClientId(), handler.shareGroup(),
                    handler.topic(), topic, snapshot.getMessage());
            try {
                if (handler instanceof MqttMessageValidator validator
                        && !validator.validate(handlerMessage)) {
                    continue;
                }
            } catch (RuntimeException e) {
                LOG.error("MQTT message validation failed, clientKey={}, topic={}, handler={}",
                        context.getClientKey(), topic, handler.getClass().getName(), e);
                continue;
            }
            invokeHandler(handler, handlerMessage);
        }
    }

    @Override
    public void deliveryComplete(IMqttToken token) {
        LOG.debug("MQTT delivery complete, clientKey={}", context.getClientKey());
    }

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        LOG.info("MQTT client connected, clientKey={}, reconnect={}",
                context.getClientKey(), reconnect);
        connectedHandler.accept(context);
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        LOG.debug("MQTT auth packet arrived, clientKey={}, reasonCode={}",
                context.getClientKey(), reasonCode);
    }

    private void invokeHandler(MqttMessageHandler handler, MqttMessageContext message) {
        try {
            handler.handle(message);
        } catch (RuntimeException e) {
            LOG.error("MQTT message handler failed, clientKey={}, topic={}, handler={}",
                    context.getClientKey(), message.getTopic(), handler.getClass().getName(), e);
        }
    }
}

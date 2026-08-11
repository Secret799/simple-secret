package com.ss.mqttv3.config;

import com.ss.mqttv3.handler.MqttMessageHandler;
import com.ss.mqttv3.message.MqttMessageContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttClientOptionsTest {

    @Test
    void exposesSafeConnectionDefaults() {
        MqttClientOptions options = new MqttClientOptions();

        assertFalse(options.isEnabled());
        assertTrue(options.isCleanSession());
        assertEquals(30, options.getKeepAliveSeconds());
        assertEquals(10, options.getConnectionTimeoutSeconds());
        assertEquals(10, options.getPublishTimeoutSeconds());
        assertTrue(options.isReconnectEnabled());
        assertEquals(1_000L, options.getReconnectDelayMillis());
        assertFalse(options.getWill().isEnabled());
        assertEquals(0, options.getWill().getQos());
        assertFalse(options.getWill().isRetained());
    }

    @Test
    void generatedClientIdIsStableUntilConfiguredIdChanges() {
        MqttClientOptions options = new MqttClientOptions();

        String generated = options.resolveClientId();

        assertEquals(generated, options.resolveClientId());
        options.setClientId("configured-client");
        assertEquals("configured-client", options.resolveClientId());
        options.setClientId("other-client");
        assertEquals("other-client", options.resolveClientId());
    }

    @Test
    void handlerHasNeutralDefaults() {
        MqttMessageHandler handler = new MqttMessageHandler() {
            @Override
            public String topic() {
                return "devices/+";
            }

            @Override
            public void handle(MqttMessageContext message) {
            }
        };

        assertEquals("default", handler.clientKey());
        assertEquals(0, handler.qos());
        assertEquals("", handler.shareGroup());
    }
}

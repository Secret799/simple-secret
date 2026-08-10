package com.ss.mqttv5.message;

import com.fasterxml.jackson.core.type.TypeReference;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttMessageContextTest {

    @Test
    void exposesMetadataAndUtf8Payload() {
        MqttMessage mqttMessage = new MqttMessage("你好".getBytes(StandardCharsets.UTF_8));
        MqttMessageContext context = new MqttMessageContext(
                "default", "client-1", "workers", "devices/+", "devices/a", mqttMessage);

        assertEquals("default", context.getClientKey());
        assertEquals("client-1", context.getClientId());
        assertEquals("workers", context.getShareGroup());
        assertEquals("devices/+", context.getSubscribeTopic());
        assertEquals("devices/a", context.getTopic());
        assertEquals("你好", context.getPayloadAsString());
        assertNotSame(mqttMessage, context.getMessage());
        assertEquals("你好", new String(context.getMessage().getPayload(), StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class,
                () -> context.getMessage().setPayload("changed".getBytes(StandardCharsets.UTF_8)));
        mqttMessage.getPayload()[0] = 0;
        assertEquals("你好", context.getPayloadAsString());
    }

    @Test
    void convertsPayloadThroughJsonModule() {
        MqttMessage objectMessage = new MqttMessage(
                "{\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8));
        MqttMessageContext objectContext = context(objectMessage);
        MqttMessage listMessage = new MqttMessage(
                "[{\"name\":\"Ada\"}]".getBytes(StandardCharsets.UTF_8));
        MqttMessageContext listContext = context(listMessage);

        assertEquals(new Person("Ada"), objectContext.getPayload(Person.class));
        assertEquals(List.of(new Person("Ada")),
                listContext.getPayload(new TypeReference<List<Person>>() { }));
    }

    private MqttMessageContext context(MqttMessage message) {
        return new MqttMessageContext("default", "client-1", "", "devices/+", "devices/a", message);
    }

    private record Person(String name) {
    }
}

package com.ss.mqttv3.message;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ss.mqttv3.exception.MqttOperationException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void convertsJsonPayloadWithoutJsonStarter() {
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

    @Test
    void convertsInvalidJsonFailureToMqttOperationException() {
        MqttMessageContext context = context(new MqttMessage(
                "{\"status\":\"sensitive-value\"}".getBytes(StandardCharsets.UTF_8)));

        MqttOperationException exception = assertThrows(
                MqttOperationException.class, () -> context.getPayload(StatusPayload.class));

        assertFalse(exception.getCause().getMessage().contains("sensitive-value"));
    }

    @Test
    void preservesJsonStarterPayloadCompatibility() {
        MqttMessageContext emptyContext = context(new MqttMessage(new byte[0]));
        MqttMessageContext extendedContext = context(new MqttMessage(
                "{\"name\":\"Ada\",\"unknown\":true}".getBytes(StandardCharsets.UTF_8)));
        MqttMessageContext timeContext = context(new MqttMessage(
                "{\"createdAt\":\"2026-08-12T11:30:00\"}".getBytes(StandardCharsets.UTF_8)));
        MqttMessageContext legacyTimeContext = context(new MqttMessage(
                "{\"createdAt\":\"2026-08-12T11:30:00\"}".getBytes(StandardCharsets.UTF_8)));

        assertNull(emptyContext.getPayload(Person.class));
        assertEquals(new Person("Ada"), extendedContext.getPayload(Person.class));
        assertEquals(new TimedPayload(LocalDateTime.of(2026, 8, 12, 11, 30)),
                timeContext.getPayload(TimedPayload.class));
        Date expectedDate = Date.from(LocalDateTime.of(2026, 8, 12, 11, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        assertEquals(new LegacyTimedPayload(expectedDate),
                legacyTimeContext.getPayload(LegacyTimedPayload.class));
    }

    private MqttMessageContext context(MqttMessage message) {
        return new MqttMessageContext("default", "client-1", "", "devices/+", "devices/a", message);
    }

    private record Person(String name) {
    }

    private record TimedPayload(LocalDateTime createdAt) {
    }

    private record LegacyTimedPayload(Date createdAt) {
    }

    private record StatusPayload(Status status) {
    }

    private enum Status {
        READY
    }
}

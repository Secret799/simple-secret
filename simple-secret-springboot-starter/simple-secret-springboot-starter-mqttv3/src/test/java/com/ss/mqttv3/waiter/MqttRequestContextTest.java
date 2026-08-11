package com.ss.mqttv3.waiter;

import com.ss.mqttv3.message.MqttMessageContext;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttRequestContextTest {

    @Test
    void completesOnlyMatchingClientAndCorrelation() {
        DefaultMqttResponseWaiter waiter = new DefaultMqttResponseWaiter();
        MqttRequestContext clientA = new MqttRequestContext("client-a", waiter);
        MqttRequestContext clientB = new MqttRequestContext("client-b", waiter);
        MqttCorrelationExtractor extractor = (type, payload) -> payload;
        clientA.register("reply/+", "request-1", extractor);
        clientB.register("reply/+", "request-1", extractor);

        assertTrue(clientA.handle(message("client-a", "reply/device", "request-1")));

        assertEquals("request-1", clientA.await("request-1", Duration.ofMillis(20))
                .orElseThrow().getPayloadAsString());
        assertTrue(clientB.await("request-1", Duration.ofMillis(1)).isEmpty());
        assertEquals(0, clientA.registrationCount());
        clientB.cancel("reply/+", "request-1");
        assertEquals(0, clientB.registrationCount());
    }

    @Test
    void ignoresTopicMismatchAndBlankExtractedKey() {
        DefaultMqttResponseWaiter waiter = new DefaultMqttResponseWaiter();
        MqttRequestContext context = new MqttRequestContext("client-a", waiter);
        context.register("reply/+", "request-1", (type, payload) -> "");

        assertFalse(context.handle(message("client-a", "other/device", "request-1")));
        assertFalse(context.handle(message("client-a", "reply/device", "request-1")));

        context.cancel("reply/+", "request-1");
        assertEquals(0, context.registrationCount());
    }

    @Test
    void separatesClientAndCorrelationKeysWithoutDelimiterCollisions() {
        DefaultMqttResponseWaiter waiter = new DefaultMqttResponseWaiter();
        MqttRequestContext first = new MqttRequestContext("a::b", waiter);
        MqttRequestContext second = new MqttRequestContext("a", waiter);
        MqttCorrelationExtractor extractor = (type, payload) -> payload;
        first.register("reply/first", "c", extractor);

        assertDoesNotThrow(() -> second.register("reply/second", "b::c", extractor));
        assertTrue(first.handle(message("a::b", "reply/first", "c")));
        assertTrue(second.handle(message("a", "reply/second", "b::c")));
        assertEquals("reply/first", first.await("c", Duration.ofMillis(20))
                .orElseThrow().getTopic());
        assertEquals("reply/second", second.await("b::c", Duration.ofMillis(20))
                .orElseThrow().getTopic());
    }

    @Test
    void responseMetadataUsesMatchedReplyFilter() {
        DefaultMqttResponseWaiter waiter = new DefaultMqttResponseWaiter();
        MqttRequestContext context = new MqttRequestContext("client-a", waiter);
        context.register("reply/+", "request-1", (type, payload) -> payload);
        MqttMessageContext incoming = message(
                "client-a", "reply/device", "reply/device", "request-1");

        assertTrue(context.handle(incoming));

        MqttMessageContext response = context.await("request-1", Duration.ofMillis(20))
                .orElseThrow();
        assertEquals("reply/+", response.getSubscribeTopic());
        assertEquals("reply/device", response.getTopic());
    }

    @Test
    void cancelAllReleasesEveryRegistrationAndWaiter() {
        DefaultMqttResponseWaiter waiter = new DefaultMqttResponseWaiter();
        MqttRequestContext context = new MqttRequestContext("client-a", waiter);
        MqttCorrelationExtractor extractor = (type, payload) -> payload;
        context.register("reply/one", "one", extractor);
        context.register("reply/two", "two", extractor);

        context.cancelAll();

        assertEquals(0, context.registrationCount());
        assertEquals(0, waiter.pendingCount());
    }

    private MqttMessageContext message(String clientKey, String topic, String payload) {
        return message(clientKey, topic, "reply/+", payload);
    }

    private MqttMessageContext message(String clientKey, String topic,
                                       String subscribeTopic, String payload) {
        return new MqttMessageContext(clientKey, clientKey + "-id", "", subscribeTopic, topic,
                new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));
    }
}

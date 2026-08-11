package com.ss.mqttv3.waiter;

import com.ss.mqttv3.message.MqttMessageContext;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMqttResponseWaiterTest {

    @Test
    void completesRegisteredWaiter() throws Exception {
        DefaultMqttResponseWaiter waiter = new DefaultMqttResponseWaiter();
        MqttMessageContext message = message("response-1");
        waiter.register("request-1");
        CompletableFuture<Optional<MqttMessageContext>> awaiting = CompletableFuture.supplyAsync(
                () -> waiter.await("request-1", Duration.ofSeconds(1)));

        assertTrue(waiter.complete("request-1", message));

        assertEquals(message, awaiting.get(1, TimeUnit.SECONDS).orElseThrow());
        assertEquals(0, waiter.pendingCount());
    }

    @Test
    void timeoutAndCancelRemoveWaiters() {
        DefaultMqttResponseWaiter waiter = new DefaultMqttResponseWaiter();
        waiter.register("timeout");

        assertTrue(waiter.await("timeout", Duration.ofMillis(1)).isEmpty());
        assertEquals(0, waiter.pendingCount());

        waiter.register("cancelled");
        waiter.cancel("cancelled");
        assertFalse(waiter.complete("cancelled", message("late")));
        assertEquals(0, waiter.pendingCount());
    }

    @Test
    void rejectsDuplicateRegistration() {
        DefaultMqttResponseWaiter waiter = new DefaultMqttResponseWaiter();
        waiter.register("same-key");

        assertThrows(IllegalStateException.class, () -> waiter.register("same-key"));
    }

    private MqttMessageContext message(String payload) {
        return new MqttMessageContext("default", "client-1", "", "reply/+", "reply/1",
                new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));
    }
}

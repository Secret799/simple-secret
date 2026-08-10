package com.ss.mqttv5.client;

import com.ss.mqttv5.config.MqttClientOptions;
import com.ss.mqttv5.handler.MqttMessageHandler;
import com.ss.mqttv5.message.MqttMessageContext;
import com.ss.mqttv5.waiter.DefaultMqttResponseWaiter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttClientContextTest {

    @Test
    void storesAllMatchingHandlersAndInvalidatesLookupCache() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MqttClientContext context = context(executor);
            MqttMessageHandler first = handler("devices/+");
            MqttMessageHandler second = handler("devices/+");
            MqttMessageHandler broad = handler("devices/#");
            context.addHandler(first);
            context.addHandler(first);
            context.addHandler(second);

            assertEquals(List.of(first, second), context.getMatchingHandlers("devices/a"));

            context.addHandler(broad);
            assertEquals(List.of(first, second, broad), context.getMatchingHandlers("devices/a"));
            context.removeHandlers("devices/+");
            assertEquals(List.of(broad), context.getMatchingHandlers("devices/a"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void tracksClosingState() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MqttClientContext context = context(executor);
            assertFalse(context.isClosing());
            context.markClosing();
            assertTrue(context.isClosing());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentAddCannotBeOverwrittenByStaleCacheLookup() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lookupEntered = new CountDownLatch(1);
        CountDownLatch allowLookup = new CountDownLatch(1);
        try {
            MqttClientContext context = context(executor);
            AtomicBoolean firstTopicRead = new AtomicBoolean(true);
            MqttMessageHandler blocking = new MqttMessageHandler() {
                @Override
                public String topic() {
                    if (firstTopicRead.compareAndSet(true, false)) {
                        lookupEntered.countDown();
                        awaitUnchecked(allowLookup);
                    }
                    return "devices/+";
                }

                @Override
                public void handle(MqttMessageContext message) {
                }
            };
            MqttMessageHandler added = handler("devices/#");
            context.addHandler(blocking);
            Future<List<MqttMessageHandler>> staleLookup = executor.submit(
                    () -> context.getMatchingHandlers("devices/a"));
            assertTrue(lookupEntered.await(1, TimeUnit.SECONDS));

            context.addHandler(added);
            allowLookup.countDown();

            assertEquals(List.of(blocking), staleLookup.get());
            assertEquals(List.of(blocking, added),
                    context.getMatchingHandlers("devices/a"));
        } finally {
            allowLookup.countDown();
            executor.shutdownNow();
        }
    }

    private static void awaitUnchecked(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private MqttClientContext context(ExecutorService executor) {
        return new MqttClientContext("default", new FakeMqttClientAdapter("client-1"),
                new MqttClientOptions(), new DefaultMqttResponseWaiter(), executor);
    }

    private MqttMessageHandler handler(String filter) {
        return new MqttMessageHandler() {
            @Override
            public String topic() {
                return filter;
            }

            @Override
            public void handle(MqttMessageContext message) {
            }
        };
    }
}

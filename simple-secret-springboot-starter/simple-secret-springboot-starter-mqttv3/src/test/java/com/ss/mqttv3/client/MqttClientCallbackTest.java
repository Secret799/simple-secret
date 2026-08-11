package com.ss.mqttv3.client;

import com.ss.mqttv3.config.MqttClientOptions;
import com.ss.mqttv3.handler.MqttMessageHandler;
import com.ss.mqttv3.handler.MqttMessageValidator;
import com.ss.mqttv3.message.MqttMessageContext;
import com.ss.mqttv3.waiter.DefaultMqttResponseWaiter;
import com.ss.mqttv3.waiter.MqttCorrelationExtractor;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttClientCallbackTest {

    @Test
    void completesRequestBeforeDispatchingHandlers() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MqttClientContext context = context(executor);
            MqttCorrelationExtractor extractor = (type, payload) -> payload;
            context.getRequestContext().register("reply/+", "request-1", extractor);
            MqttClientCallback callback = new MqttClientCallback(context, ignored -> { }, ignored -> { });

            callback.messageArrived("reply/device", message("request-1"));

            assertEquals("request-1", context.getRequestContext()
                    .await("request-1", Duration.ofMillis(20)).orElseThrow().getPayloadAsString());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void isolatesValidatorAndHandlerFailures() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MqttClientContext context = context(executor);
            CountDownLatch successfulHandle = new CountDownLatch(1);
            AtomicInteger rejectedHandles = new AtomicInteger();
            context.addHandler(new ValidatingHandler(false, false, rejectedHandles, null));
            context.addHandler(new ValidatingHandler(true, true, new AtomicInteger(), null));
            context.addHandler(new ValidatingHandler(true, false, new AtomicInteger(),
                    new IllegalStateException("handler failure")));
            context.addHandler(new ValidatingHandler(true, false, new AtomicInteger(), null) {
                @Override
                public void handle(MqttMessageContext message) {
                    successfulHandle.countDown();
                }
            });
            MqttClientCallback callback = new MqttClientCallback(context, ignored -> { }, ignored -> { });

            callback.messageArrived("devices/a", message("payload"));

            assertTrue(successfulHandle.await(1, TimeUnit.SECONDS));
            assertEquals(0, rejectedHandles.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void extractorValidatorAndHandlerRunOutsideCallbackThread() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
                new Thread(runnable, "mqtt-dispatch-test"));
        try {
            MqttClientContext context = context(executor);
            AtomicReference<String> extractorThread = new AtomicReference<>();
            AtomicReference<String> validatorThread = new AtomicReference<>();
            AtomicReference<String> handlerThread = new AtomicReference<>();
            CountDownLatch handled = new CountDownLatch(1);
            context.getRequestContext().register("devices/+", "payload", (type, payload) -> {
                extractorThread.set(Thread.currentThread().getName());
                return payload;
            });
            context.addHandler(new ValidatingHandler(true, false, new AtomicInteger(), null) {
                @Override
                public boolean validate(MqttMessageContext message) {
                    validatorThread.set(Thread.currentThread().getName());
                    return true;
                }

                @Override
                public void handle(MqttMessageContext message) {
                    handlerThread.set(Thread.currentThread().getName());
                    handled.countDown();
                }
            });
            String callbackThread = Thread.currentThread().getName();

            new MqttClientCallback(context, ignored -> { }, ignored -> { })
                    .messageArrived("devices/a", message("payload"));

            assertTrue(handled.await(1, TimeUnit.SECONDS));
            assertEquals("mqtt-dispatch-test", extractorThread.get());
            assertEquals("mqtt-dispatch-test", validatorThread.get());
            assertEquals("mqtt-dispatch-test", handlerThread.get());
            assertTrue(!callbackThread.equals(handlerThread.get()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void oneHandlerCannotMutatePayloadSeenByAnotherHandler() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            MqttClientContext context = context(executor);
            CountDownLatch handled = new CountDownLatch(1);
            AtomicReference<String> observed = new AtomicReference<>();
            context.addHandler(new ValidatingHandler(true, false, new AtomicInteger(), null) {
                @Override
                public void handle(MqttMessageContext message) {
                    message.getMessage().getPayload()[0] = 'X';
                }
            });
            context.addHandler(new ValidatingHandler(true, false, new AtomicInteger(), null) {
                @Override
                public void handle(MqttMessageContext message) {
                    observed.set(message.getPayloadAsString());
                    handled.countDown();
                }
            });

            new MqttClientCallback(context, ignored -> { }, ignored -> { })
                    .messageArrived("devices/a", message("payload"));

            assertTrue(handled.await(1, TimeUnit.SECONDS));
            assertEquals("payload", observed.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private MqttClientContext context(ExecutorService executor) {
        return new MqttClientContext("default", new FakeMqttClientAdapter("client-1"),
                new MqttClientOptions(), new DefaultMqttResponseWaiter(), executor);
    }

    private MqttMessage message(String payload) {
        return new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static class ValidatingHandler implements MqttMessageHandler, MqttMessageValidator {
        private final boolean valid;
        private final boolean validationThrows;
        private final AtomicInteger handles;
        private final RuntimeException handlerFailure;

        private ValidatingHandler(boolean valid, boolean validationThrows,
                                  AtomicInteger handles, RuntimeException handlerFailure) {
            this.valid = valid;
            this.validationThrows = validationThrows;
            this.handles = handles;
            this.handlerFailure = handlerFailure;
        }

        @Override
        public String topic() {
            return "devices/+";
        }

        @Override
        public boolean validate(MqttMessageContext message) {
            if (validationThrows) {
                throw new IllegalArgumentException("invalid signature");
            }
            return valid;
        }

        @Override
        public void handle(MqttMessageContext message) {
            handles.incrementAndGet();
            if (handlerFailure != null) {
                throw handlerFailure;
            }
        }
    }
}

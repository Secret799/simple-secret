package com.ss.mqttv5.waiter;

import com.ss.mqttv5.message.MqttMessageContext;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基于 {@link CompletableFuture} 的默认 MQTT 响应等待器。
 */
public class DefaultMqttResponseWaiter implements MqttResponseWaiter {
    private final ConcurrentHashMap<String, CompletableFuture<MqttMessageContext>> pending =
            new ConcurrentHashMap<>();

    @Override
    public void register(String waitKey) {
        requireKey(waitKey);
        CompletableFuture<MqttMessageContext> previous =
                pending.putIfAbsent(waitKey, new CompletableFuture<>());
        if (previous != null) {
            throw new IllegalStateException("MQTT wait key is already registered: " + waitKey);
        }
    }

    @Override
    public Optional<MqttMessageContext> await(String waitKey, Duration timeout) {
        requireKey(waitKey);
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must not be negative");
        }
        CompletableFuture<MqttMessageContext> future = pending.get(waitKey);
        if (future == null) {
            throw new IllegalStateException("MQTT wait key is not registered: " + waitKey);
        }
        try {
            return Optional.of(future.get(timeout.toNanos(), TimeUnit.NANOSECONDS));
        } catch (TimeoutException | CancellationException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for MQTT response", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unable to wait for MQTT response", e.getCause());
        } finally {
            pending.remove(waitKey, future);
        }
    }

    @Override
    public boolean complete(String waitKey, MqttMessageContext message) {
        requireKey(waitKey);
        if (message == null) {
            throw new IllegalArgumentException("Response message must not be null");
        }
        CompletableFuture<MqttMessageContext> future = pending.get(waitKey);
        return future != null && future.complete(message);
    }

    @Override
    public void cancel(String waitKey) {
        requireKey(waitKey);
        CompletableFuture<MqttMessageContext> future = pending.remove(waitKey);
        if (future != null) {
            future.cancel(false);
        }
    }

    int pendingCount() {
        return pending.size();
    }

    private static void requireKey(String waitKey) {
        if (waitKey == null || waitKey.isBlank()) {
            throw new IllegalArgumentException("MQTT wait key must not be blank");
        }
    }
}

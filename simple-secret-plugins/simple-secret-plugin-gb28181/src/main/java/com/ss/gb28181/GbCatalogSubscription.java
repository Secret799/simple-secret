package com.ss.gb28181;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Managed whole-catalog subscription. The server owns renewal and signaling resources. */
public final class GbCatalogSubscription implements AutoCloseable {
    private final String deviceId;
    private final String callId;
    private final GbCatalogSubscriptionRequest request;
    private final CompletableFuture<Void> completion;
    private final Supplier<CompletableFuture<Void>> stop;

    GbCatalogSubscription(String deviceId, String callId, GbCatalogSubscriptionRequest request,
                          CompletableFuture<Void> completion, Supplier<CompletableFuture<Void>> stop) {
        this.deviceId = deviceId;
        this.callId = callId;
        this.request = request;
        this.completion = completion;
        this.stop = stop;
    }

    public String deviceId() {
        return deviceId;
    }

    public String callId() {
        return callId;
    }

    public GbCatalogSubscriptionRequest request() {
        return request;
    }

    /** Normal on acknowledged stop or remote termination; exceptional on expiry, signaling failure or offline. */
    public CompletionStage<Void> completion() {
        return completion.minimalCompletionStage();
    }

    /** Idempotently sends in-dialog SUBSCRIBE Expires:0 and waits for termination. */
    public CompletableFuture<Void> stop() {
        return stop.get();
    }

    /** Initiates stop without blocking. */
    @Override
    public void close() {
        stop();
    }
}

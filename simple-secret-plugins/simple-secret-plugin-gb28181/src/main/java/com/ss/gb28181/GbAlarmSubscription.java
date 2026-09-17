package com.ss.gb28181;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Managed Alarm subscription. SIP acceptance does not guarantee an alarm or business persistence.
 * Use asynchronous continuations; the server owns renewal and signaling resources.
 */
public final class GbAlarmSubscription implements AutoCloseable {
    private final String deviceId, targetId, callId;
    private final GbAlarmSubscriptionRequest request;
    private final CompletableFuture<Void> completion;
    private final Supplier<CompletableFuture<Void>> stop;

    GbAlarmSubscription(String deviceId, String targetId, String callId, GbAlarmSubscriptionRequest request,
                        CompletableFuture<Void> completion, Supplier<CompletableFuture<Void>> stop) {
        this.deviceId = deviceId; this.targetId = targetId; this.callId = callId;
        this.request = request; this.completion = completion; this.stop = stop;
    }
    public String deviceId() { return deviceId; }
    public String targetId() { return targetId; }
    public String callId() { return callId; }
    public GbAlarmSubscriptionRequest request() { return request; }
    /** Normal on acknowledged stop or remote termination; exceptional on expiry, signaling failure or offline. */
    public CompletionStage<Void> completion() { return completion.minimalCompletionStage(); }
    /** Idempotently sends in-dialog SUBSCRIBE Expires:0 and waits for acknowledgment or terminal NOTIFY.
     * Canceling this observation future does not cancel cleanup.
     */
    public CompletableFuture<Void> stop() { return stop.get(); }
    /** Initiates stop without blocking. */
    @Override public void close() { stop(); }
}

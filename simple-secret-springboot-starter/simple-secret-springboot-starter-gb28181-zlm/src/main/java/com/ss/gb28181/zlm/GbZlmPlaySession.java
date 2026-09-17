package com.ss.gb28181.zlm;

import com.ss.gb28181.GbPlaySession;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** A negotiated SIP session and its ZLM stream identity. Media readiness is separate from SIP success. */
public record GbZlmPlaySession(String stream, GbPlaySession sipSession) implements AutoCloseable {
    public GbZlmPlaySession {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(sipSession, "sipSession");
    }

    public String app() { return "rtp"; }
    public CompletableFuture<Void> stop() { return sipSession.stop(); }
    public CompletionStage<Void> completion() { return sipSession.completion(); }
    @Override public void close() { stop(); }
}

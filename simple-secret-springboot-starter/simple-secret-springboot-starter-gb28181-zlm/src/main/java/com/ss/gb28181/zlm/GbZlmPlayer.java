package com.ss.gb28181.zlm;

import com.ss.gb28181.Gb28181Server;
import com.ss.gb28181.GbDownloadRequest;
import com.ss.gb28181.GbPlaySession;
import com.ss.gb28181.GbPlaybackRange;
import com.ss.gb28181.GbRtpTarget;
import com.ss.zlm4j.service.IZlmMediaService;
import com.ss.zlm4j.service.domain.bo.OpenRtpServerBO;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Owns bounded RTP receivers, but not the injected SIP server or ZLM service.
 * Native operations run on one daemon worker; SIP callbacks only signal state changes.
 * Completion means SIP negotiation, not media readiness. Use asynchronous business continuations.
 */
public final class GbZlmPlayer implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(GbZlmPlayer.class.getName());
    private final Gb28181Server server;
    private final IZlmMediaService media;
    private final String address;
    private final GbRtpTarget.Transport transport;
    private final int capacity;
    private final Object gate = new Object();
    private final Set<Attempt> attempts = new HashSet<>();
    private final ThreadPoolExecutor executor;
    private volatile Thread worker;
    private volatile boolean closed;
    private CompletableFuture<Void> closePass;

    public GbZlmPlayer(Gb28181Server server, IZlmMediaService media, String advertisedAddress,
                       GbRtpTarget.Transport transport, int maxSessions) {
        this.server = Objects.requireNonNull(server, "server");
        this.media = Objects.requireNonNull(media, "media");
        // Reuse the signaling API's IP/transport validation without allocating a receiver.
        GbRtpTarget validated = new GbRtpTarget(advertisedAddress, 1, transport);
        this.address = validated.address();
        this.transport = validated.transport();
        if (maxSessions < 1 || maxSessions > 10000)
            throw new IllegalArgumentException("maxSessions must be between 1 and 10000");
        capacity = maxSessions;
        // Each active attempt can queue at most one coalesced task, plus one close barrier.
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(maxSessions + 1), runnable -> {
                    Thread thread = new Thread(runnable, "simple-secret-gb-zlm");
                    thread.setDaemon(true);
                    worker = thread;
                    return thread;
                });
    }

    /** Reserves capacity immediately; native allocation and INVITE creation happen on the worker. */
    public CompletableFuture<GbZlmPlaySession> play(String deviceId, String channelId) {
        return begin(deviceId, channelId, null, null);
    }

    /** Allocates a receiver for the requested history range; controls are available through sipSession(). */
    public CompletableFuture<GbZlmPlaySession> playback(String deviceId, String channelId, GbPlaybackRange range) {
        return begin(deviceId, channelId, Objects.requireNonNull(range, "range"), null);
    }

    /** Allocates a receiver for a download stream; file recording remains the host's responsibility. */
    public CompletableFuture<GbZlmPlaySession> download(String deviceId, String channelId, GbDownloadRequest request) {
        return begin(deviceId, channelId, null, Objects.requireNonNull(request, "request"));
    }

    private CompletableFuture<GbZlmPlaySession> begin(String deviceId, String channelId, GbPlaybackRange range,
                                                     GbDownloadRequest download) {
        requireId(deviceId);
        requireId(channelId);
        synchronized (gate) {
            if (closed) return CompletableFuture.failedFuture(new IllegalStateException("GB ZLM player is closed"));
            if (attempts.size() >= capacity)
                return CompletableFuture.failedFuture(new IllegalStateException("GB ZLM session capacity exhausted"));
            Attempt attempt = new Attempt(deviceId, channelId, range, download);
            attempts.add(attempt);
            attempt.signal();
            return attempt.result;
        }
    }

    /**
     * Stops SIP without awaiting BYE, and waits up to five seconds for local native cleanup.
     * Failed releases remain owned and may be retried by calling close again.
     */
    @Override public void close() {
        CompletableFuture<Void> pass;
        synchronized (gate) {
            closed = true;
            if (executor.isShutdown()) return;
            if (closePass == null || closePass.isDone()) {
                closePass = new CompletableFuture<>();
                CompletableFuture<Void> current = closePass;
                attempts.forEach(Attempt::signal);
                executor.execute(() -> {
                    synchronized (gate) {
                        if (attempts.isEmpty()) {
                            executor.shutdown();
                            current.complete(null);
                        } else {
                            IllegalStateException failure = new IllegalStateException("RTP receivers could not be released");
                            attempts.forEach(attempt -> {
                                if (attempt.cleanupFailure != null) failure.addSuppressed(attempt.cleanupFailure);
                            });
                            current.completeExceptionally(failure);
                        }
                    }
                });
            }
            pass = closePass;
        }
        // A user continuation may invoke close from this worker. Its queued cleanup must be allowed to run.
        if (Thread.currentThread() == worker) return;
        try {
            pass.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while closing GB ZLM player", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Cannot finish closing GB ZLM player within five seconds", e);
        }
    }

    private static void requireId(String id) {
        if (id == null || !id.matches("[0-9]{20}"))
            throw new IllegalArgumentException("Device and channel IDs must contain exactly 20 digits");
    }

    private static void stopSession(GbPlaySession session) {
        if (session == null) return;
        try { session.stop(); }
        catch (RuntimeException e) { LOG.log(System.Logger.Level.WARNING, "Cannot stop GB SIP session", e); }
    }

    private final class Attempt implements Runnable {
        private final String deviceId;
        private final String channelId;
        private final GbPlaybackRange range;
        private final GbDownloadRequest download;
        private final String stream = "gb-" + UUID.randomUUID();
        private final CompletableFuture<GbZlmPlaySession> result = new CompletableFuture<>();
        private boolean scheduled;
        private boolean dirty;
        private volatile boolean retired;
        private volatile boolean closing;
        private boolean allocated;
        private boolean started;
        private boolean published;
        private volatile boolean ended;
        private CompletableFuture<GbPlaySession> invite;
        private volatile GbPlaySession session;
        private volatile Throwable failure;
        private volatile Throwable cleanupFailure;

        Attempt(String deviceId, String channelId, GbPlaybackRange range, GbDownloadRequest download) {
            this.deviceId = deviceId;
            this.channelId = channelId;
            this.range = range;
            this.download = download;
            result.whenComplete((value, error) -> {
                if (result.isCompletedExceptionally() && !closing) signal();
            });
        }

        // Coalescing keeps the queue bounded even if cancellation, SIP completion and close race.
        synchronized void signal() {
            if (retired || executor.isShutdown()) return;
            dirty = true;
            if (!scheduled) {
                scheduled = true;
                executor.execute(this);
            }
        }

        @Override public void run() {
            while (true) {
                synchronized (this) {
                    if (!dirty) { scheduled = false; return; }
                    dirty = false;
                }
                if (retired) continue;
                try { advance(); }
                catch (RuntimeException | Error e) {
                    failure = e;
                    release();
                }
            }
        }

        private void advance() {
            if (closed || result.isCompletedExceptionally() || closing || failure != null || ended) {
                release();
                return;
            }
            if (!started) {
                started = true;
                // Mark ownership before calling native so an exception after allocation is still cleaned up.
                allocated = true;
                Integer port = media.openRtpServer(new OpenRtpServerBO().setPort(0)
                        .setTcpMode(transport == GbRtpTarget.Transport.UDP ? 0 : 1).setStream(stream));
                if (port == null || port < 1 || port > 65535)
                    throw new IllegalStateException("ZLM did not allocate a valid RTP receiver port");
                if (closed || result.isCompletedExceptionally()) { release(); return; }
                GbRtpTarget target = new GbRtpTarget(address, port, transport);
                invite = Objects.requireNonNull(download != null ? server.download(deviceId, channelId, target, download)
                        : range == null ? server.play(deviceId, channelId, target)
                        : server.playback(deviceId, channelId, target, range), "SIP play future");
                invite.whenComplete(this::negotiated);
            }
            GbPlaySession established = session;
            if (established != null && !published) {
                published = true;
                established.completion().whenComplete((ignored, error) -> { ended = true; signal(); });
                if (!result.complete(new GbZlmPlaySession(stream, established))) release();
            }
        }

        private void negotiated(GbPlaySession established, Throwable error) {
            boolean late;
            synchronized (this) {
                late = retired || closing || closed || result.isCompletedExceptionally();
                if (!late) {
                    session = established;
                    failure = error;
                    if (established == null && error == null)
                        failure = new IllegalStateException("SIP play completed without a session");
                    signal();
                }
            }
            // Never run native cleanup here: this callback may hold SIP stack locks.
            if (late) stopSession(established);
        }

        private void release() {
            synchronized (this) { closing = true; }
            if (invite != null && !invite.isDone()) invite.cancel(false);
            stopSession(session);
            try {
                if (allocated) media.closeRtpServer(stream);
                allocated = false;
                retired = true;
                synchronized (gate) { attempts.remove(this); }
                cleanupFailure = null;
            } catch (RuntimeException | Error e) {
                cleanupFailure = e;
                if (failure != null && failure != e) failure.addSuppressed(e);
                LOG.log(System.Logger.Level.WARNING, "Cannot release GB RTP receiver; retained for close retry", e);
            }
            if (!result.isDone()) {
                Throwable cause = failure != null ? failure : new CancellationException("GB ZLM session closed");
                if (cleanupFailure != null && cause != cleanupFailure && failure == null)
                    cause.addSuppressed(cleanupFailure);
                result.completeExceptionally(cause);
            }
        }
    }
}

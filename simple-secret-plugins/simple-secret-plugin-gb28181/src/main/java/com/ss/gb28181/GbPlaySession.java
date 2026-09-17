package com.ss.gb28181;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Established SIP live, historical playback or download dialog. Establishment does not imply media has arrived.
 * Use asynchronous continuations for business work. The server owns signaling; the host owns RTP reception.
 */
public final class GbPlaySession implements AutoCloseable {
    private final String deviceId;
    private final String channelId;
    private final String callId;
    private final String ssrc;
    private final GbRtpTarget target;
    private final GbPlaybackRange playbackRange;
    private final GbDownloadRequest downloadRequest;
    private final OptionalLong downloadFileSize;
    private final Function<GbPlaybackControl, CompletableFuture<Void>> control;
    private final CompletableFuture<Void> completion;
    private final Supplier<CompletableFuture<Void>> stop;

    GbPlaySession(String deviceId, String channelId, String callId, String ssrc, GbRtpTarget target,
                  GbPlaybackRange playbackRange, GbDownloadRequest downloadRequest, OptionalLong downloadFileSize,
                  CompletableFuture<Void> completion,
                  Supplier<CompletableFuture<Void>> stop, Function<GbPlaybackControl, CompletableFuture<Void>> control) {
        this.deviceId = deviceId; this.channelId = channelId; this.callId = callId;
        this.ssrc = ssrc; this.target = target; this.completion = completion; this.stop = stop;
        this.playbackRange = playbackRange; this.control = control;
        this.downloadRequest = downloadRequest; this.downloadFileSize = downloadFileSize;
    }
    public String deviceId() { return deviceId; }
    public String channelId() { return channelId; }
    public String callId() { return callId; }
    public String ssrc() { return ssrc; }
    public GbRtpTarget target() { return target; }
    /** The requested historical playback or download interval, empty for live playback. */
    public Optional<GbPlaybackRange> playbackRange() { return Optional.ofNullable(playbackRange); }
    /** The original download request, empty for live and historical playback. */
    public Optional<GbDownloadRequest> downloadRequest() { return Optional.ofNullable(downloadRequest); }
    /** Optional bytes declared by the device SDP; does not imply bytes received or written to disk. */
    public OptionalLong downloadFileSize() { return downloadFileSize; }
    /** Sends one historical control, completing on its SIP/MANSRTSP acknowledgment.
     * Only established historical playback sessions accept controls; downloads reject them.
     * One control may be pending per session.
     * Canceling or externally completing the future releases the wait without retracting the action.
     */
    public CompletableFuture<Void> control(GbPlaybackControl command) { return control.apply(command); }
    /** Ends normally on local/remote BYE, exceptionally on offline, shutdown or signaling failure. */
    public CompletionStage<Void> completion() { return completion.minimalCompletionStage(); }
    /** Idempotently requests BYE. Completes on its final response or fails on stop timeout. */
    public CompletableFuture<Void> stop() { return stop.get(); }
    /** Initiates stop without blocking. Use stop() to observe the outcome. */
    @Override public void close() { stop(); }
}

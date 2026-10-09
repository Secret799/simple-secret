package com.ss.gb28181;

import java.util.Objects;

/** Recording download interval and requested integer speed; 128 is this implementation's upper bound. */
public record GbDownloadRequest(GbPlaybackRange range, int speed) {
    public GbDownloadRequest {
        Objects.requireNonNull(range, "range");
        if (speed < 1 || speed > 128)
            throw new IllegalArgumentException("Download speed must be in 1..128");
    }

    public static GbDownloadRequest normal(GbPlaybackRange range) {
        return new GbDownloadRequest(range, 1);
    }
}

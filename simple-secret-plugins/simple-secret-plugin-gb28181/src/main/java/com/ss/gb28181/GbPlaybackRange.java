package com.ss.gb28181;

import java.time.Instant;
import java.util.Objects;

/** Historical recording interval in whole UNIX seconds; the host supplies the device's explicit time zone conversion. */
public record GbPlaybackRange(Instant startTime, Instant endTime) {
    public GbPlaybackRange {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        if (startTime.getNano() != 0 || endTime.getNano() != 0
                || startTime.getEpochSecond() < 0 || endTime.getEpochSecond() > 253402300799L
                || !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Playback interval must use whole UNIX seconds in 0..253402300799 with start before end");
        }
    }
}

package com.ss.gb28181;

import java.time.Duration;
import java.util.Objects;

/** One immutable historical playback control; device acceptance does not confirm media output. */
public final class GbPlaybackControl {
    private final Type type;
    private final Duration position;
    private final double scale;

    private GbPlaybackControl(Type type, Duration position, double scale) {
        this.type = type;
        this.position = position;
        this.scale = scale;
    }

    public static GbPlaybackControl pause() {
        return new GbPlaybackControl(Type.PAUSE, null, 1);
    }

    public static GbPlaybackControl resume() {
        return new GbPlaybackControl(Type.RESUME, null, 1);
    }

    /** Position relative to the requested recording start, in nonnegative whole seconds. */
    public static GbPlaybackControl seek(Duration position) {
        Objects.requireNonNull(position, "position");
        if (position.isNegative() || position.getNano() != 0) {
            throw new IllegalArgumentException("Playback position must use nonnegative whole seconds");
        }
        return new GbPlaybackControl(Type.SEEK, position, 1);
    }

    public static GbPlaybackControl speed(double scale) {
        if (scale != 0.25 && scale != 0.5 && scale != 1 && scale != 2 && scale != 4) {
            throw new IllegalArgumentException("Playback scale must be 0.25, 0.5, 1, 2 or 4");
        }
        return new GbPlaybackControl(Type.SPEED, null, scale);
    }

    public Type type() { return type; }

    /** Relative seek position, or null for other controls. */
    public Duration position() { return position; }

    /** Requested speed, or 1 for controls that do not change speed. */
    public double scale() { return scale; }

    public enum Type {
        PAUSE, RESUME, SEEK, SPEED
    }
}

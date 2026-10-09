package com.ss.gb28181;

import java.util.Objects;

/** One WGS84 position reported by a GB28181 device or channel. */
public record GbMobilePosition(String deviceId, String captureTime, double longitude, double latitude,
                               Double speed, Double direction, Double altitude, Double height) {
    public GbMobilePosition {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(captureTime, "captureTime");
        if (!deviceId.matches("[0-9]{20}")) {
            throw new IllegalArgumentException("deviceId must contain 20 digits");
        }
        GbMobilePositionValidation.dateTime(captureTime, "captureTime");
        finiteRange(longitude, -180, 180, "longitude");
        finiteRange(latitude, -90, 90, "latitude");
        if (speed != null && (!Double.isFinite(speed) || speed < 0)) {
            throw new IllegalArgumentException("speed must be finite and non-negative");
        }
        if (direction != null && (!Double.isFinite(direction) || direction < 0 || direction >= 360)) {
            throw new IllegalArgumentException("direction must be finite and in [0,360)");
        }
        optionalFinite(altitude, "altitude");
        optionalFinite(height, "height");
    }

    private static void finiteRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " outside supported range");
        }
    }

    private static void optionalFinite(Double value, String name) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}

package com.ss.gb28181;

/** Home-position configuration reported by a device or channel.
 * A null enabled value means the configuration was not reported; optional numeric fields stay null.
 * resetTime is in seconds; presetIndex is in 0..255.
 */
public record HomePosition(String deviceId, Boolean enabled, Integer resetTime, Integer presetIndex) {
    public HomePosition(String deviceId, boolean enabled, Integer resetTime, Integer presetIndex) {
        this(deviceId, Boolean.valueOf(enabled), resetTime, presetIndex);
    }
}

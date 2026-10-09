package com.ss.gb28181;

/** Precise PTZ position reported by a GB28181 device. */
public record PtzPosition(String deviceId, Double pan, Double tilt, Double zoom) {
}

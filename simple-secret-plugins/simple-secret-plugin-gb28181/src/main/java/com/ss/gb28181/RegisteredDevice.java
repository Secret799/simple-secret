package com.ss.gb28181;

import java.time.Instant;

/** Immutable snapshot of a currently registered, live device. */
public record RegisteredDevice(String deviceId, String protocolVersion, String transport,
                               Instant registeredUntil, Instant lastHeartbeat) { }

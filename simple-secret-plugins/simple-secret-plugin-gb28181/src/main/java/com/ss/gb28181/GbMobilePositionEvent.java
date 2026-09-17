package com.ss.gb28181;

import java.time.Instant;
import java.util.Objects;

/** A position notification together with its authenticated owner, dialog and receipt time. */
public record GbMobilePositionEvent(String sourceDeviceId, String callId, Instant receivedAt,
                                    GbMobilePositionNotification notification) {
    public GbMobilePositionEvent {
        Objects.requireNonNull(sourceDeviceId, "sourceDeviceId");
        Objects.requireNonNull(callId, "callId");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(notification, "notification");
        if (!sourceDeviceId.matches("[0-9]{20}")) {
            throw new IllegalArgumentException("sourceDeviceId must contain 20 digits");
        }
    }
}

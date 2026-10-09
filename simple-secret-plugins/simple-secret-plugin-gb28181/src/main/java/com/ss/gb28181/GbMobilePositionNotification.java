package com.ss.gb28181;

import java.util.List;
import java.util.Objects;

/** One standard 2022 MobilePosition notification batch. */
public record GbMobilePositionNotification(String deviceId, int sn, String time, int total,
                                           List<GbMobilePosition> positions) {
    public GbMobilePositionNotification {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(time, "time");
        positions = List.copyOf(positions);
        if (!deviceId.matches("[0-9]{20}")) {
            throw new IllegalArgumentException("deviceId must contain 20 digits");
        }
        if (sn <= 0) {
            throw new IllegalArgumentException("sn must be positive");
        }
        GbMobilePositionValidation.dateTime(time, "time");
        if (total < positions.size()) {
            throw new IllegalArgumentException("total must include every position");
        }
    }
}

package com.ss.gb28181;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/** Catalog subscription options using device-local, whole-second times. */
public record GbCatalogSubscriptionRequest(Duration expires,
                                           LocalDateTime startTime,
                                           LocalDateTime endTime) {
    public GbCatalogSubscriptionRequest {
        Objects.requireNonNull(expires, "expires");
        if (expires.isZero() || expires.isNegative() || expires.getNano() != 0
                || expires.getSeconds() > 86_400) {
            throw new IllegalArgumentException("Expires must be a whole number of seconds in 1..86400");
        }
        validateTime(startTime);
        validateTime(endTime);
        if (startTime != null && endTime != null && !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Catalog start time must be before end time");
        }
    }

    public static GbCatalogSubscriptionRequest all(Duration expires) {
        return new GbCatalogSubscriptionRequest(expires, null, null);
    }

    private static void validateTime(LocalDateTime time) {
        if (time != null && (time.getYear() < 1 || time.getYear() > 9999 || time.getNano() != 0)) {
            throw new IllegalArgumentException("Catalog times must use whole seconds in years 1..9999");
        }
    }
}

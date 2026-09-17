package com.ss.gb28181;

import java.time.Duration;
import java.util.Objects;

/** Lease and reporting interval for a MobilePosition subscription. */
public record GbMobilePositionSubscriptionRequest(Duration expires, Duration interval) {
    public GbMobilePositionSubscriptionRequest(Duration expires) {
        this(expires, Duration.ofSeconds(5));
    }

    public GbMobilePositionSubscriptionRequest {
        validate(expires, "Expires");
        validate(interval, "Interval");
    }

    private static void validate(Duration value, String name) {
        Objects.requireNonNull(value, name.toLowerCase());
        if (value.isZero() || value.isNegative() || value.getNano() != 0 || value.getSeconds() > 86_400) {
            throw new IllegalArgumentException(name + " must be a whole number of seconds in 1..86400");
        }
    }
}

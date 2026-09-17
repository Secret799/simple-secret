package com.ss.gb28181;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/** Alarm subscription filters using device-local, whole-second times. */
public record GbAlarmSubscriptionRequest(Duration expires,
                                         int startPriority,
                                         int endPriority,
                                         Set<Integer> methods,
                                         Integer type,
                                         LocalDateTime startTime,
                                         LocalDateTime endTime) {
    public GbAlarmSubscriptionRequest {
        Objects.requireNonNull(expires, "expires");
        Objects.requireNonNull(methods, "methods");
        if (expires.isZero() || expires.isNegative() || expires.getNano() != 0
                || expires.getSeconds() > 86_400) {
            throw new IllegalArgumentException("Expires must be a whole number of seconds in 1..86400");
        }
        if (!((startPriority == 0 && endPriority == 0)
                || (startPriority >= 1 && startPriority <= endPriority && endPriority <= 4))) {
            throw new IllegalArgumentException("Priorities must both be 0 or form an ascending range in 1..4");
        }
        methods = Set.copyOf(methods);
        if (methods.stream().anyMatch(method -> method < 1 || method > 7)) {
            throw new IllegalArgumentException("Alarm methods must be in 1..7");
        }
        if (type != null && type <= 0) {
            throw new IllegalArgumentException("Alarm type must be positive");
        }
        if ((startTime == null) != (endTime == null)) {
            throw new IllegalArgumentException("Alarm times must both be present or absent");
        }
        if (startTime != null && (startTime.getYear() < 1 || startTime.getYear() > 9999
                || endTime.getYear() < 1 || endTime.getYear() > 9999
                || startTime.getNano() != 0 || endTime.getNano() != 0
                || !startTime.isBefore(endTime))) {
            throw new IllegalArgumentException(
                    "Alarm interval must use whole seconds in years 1..9999 with start before end");
        }
    }

    public static GbAlarmSubscriptionRequest all(Duration expires) {
        return new GbAlarmSubscriptionRequest(expires, 0, 0, Set.of(), null, null, null);
    }
}

package com.ss.gb28181;

import java.util.Objects;
import java.util.Set;

/** Explicit alarm methods, and optionally one method-specific alarm type, to reset. */
public record GbAlarmResetCommand(Set<Integer> methods, Integer type) {
    public GbAlarmResetCommand {
        Objects.requireNonNull(methods, "methods");
        methods = Set.copyOf(methods);
        if (methods.stream().anyMatch(method -> method < 1 || method > 7)) {
            throw new IllegalArgumentException("Alarm methods must be in 1..7");
        }
        if (type != null) {
            if (methods.size() != 1) {
                throw new IllegalArgumentException("Alarm type requires exactly one method");
            }
            int method = methods.iterator().next();
            int maximum = switch (method) {
                case 2 -> 5;
                case 5 -> 13;
                case 6 -> 2;
                default -> throw new IllegalArgumentException("Alarm type is supported only for methods 2, 5 and 6");
            };
            if (type < 1 || type > maximum) {
                throw new IllegalArgumentException("Alarm type is outside the range for method " + method);
            }
        }
    }

    public static GbAlarmResetCommand all() {
        return new GbAlarmResetCommand(Set.of(), null);
    }
}

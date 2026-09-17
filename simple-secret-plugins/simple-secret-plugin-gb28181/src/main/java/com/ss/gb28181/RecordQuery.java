package com.ss.gb28181;

import java.time.LocalDateTime;
import java.util.Objects;

/** Recording search using device-local, whole-second times without an inferred time zone. */
public record RecordQuery(LocalDateTime startTime, LocalDateTime endTime, Type type) {
    public RecordQuery {
        Objects.requireNonNull(startTime, "startTime");
        Objects.requireNonNull(endTime, "endTime");
        Objects.requireNonNull(type, "type");
        if (startTime.getYear() < 1 || startTime.getYear() > 9999
                || endTime.getYear() < 1 || endTime.getYear() > 9999
                || startTime.getNano() != 0 || endTime.getNano() != 0
                || !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Recording interval must use whole seconds in years 1..9999 with start before end");
        }
    }

    public static RecordQuery all(LocalDateTime startTime, LocalDateTime endTime) {
        return new RecordQuery(startTime, endTime, Type.ALL);
    }

    public enum Type {
        ALL, TIME, ALARM, MANUAL
    }
}

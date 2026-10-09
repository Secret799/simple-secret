package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordQueryTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 15, 10, 0);

    @Test
    void supportsAllTypesAndWholeSecondYearBoundaries() {
        assertEquals(RecordQuery.Type.ALL, RecordQuery.all(START, START.plusSeconds(1)).type());
        for (var type : RecordQuery.Type.values()) {
            var query = new RecordQuery(LocalDateTime.of(1, 1, 1, 0, 0),
                    LocalDateTime.of(9999, 12, 31, 23, 59, 59), type);
            assertEquals(type, query.type());
        }
    }

    @Test
    void rejectsMissingUnorderedFractionalAndUnsupportedYearInputs() {
        assertThrows(NullPointerException.class, () -> RecordQuery.all(null, START));
        assertThrows(NullPointerException.class, () -> RecordQuery.all(START, null));
        assertThrows(NullPointerException.class, () -> new RecordQuery(START, START.plusSeconds(1), null));
        assertThrows(IllegalArgumentException.class, () -> RecordQuery.all(START, START));
        assertThrows(IllegalArgumentException.class, () -> RecordQuery.all(START, START.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> RecordQuery.all(START.plusNanos(1), START.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> RecordQuery.all(START, START.plusNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> RecordQuery.all(START.withYear(0), START));
        assertThrows(IllegalArgumentException.class, () -> RecordQuery.all(START, START.withYear(10000)));
    }
}

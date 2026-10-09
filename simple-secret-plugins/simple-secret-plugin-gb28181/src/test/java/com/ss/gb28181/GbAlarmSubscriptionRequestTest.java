package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbAlarmSubscriptionRequestTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 15, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    @Test
    void createsAllFilterWithMaximumSupportedDuration() {
        var request = GbAlarmSubscriptionRequest.all(Duration.ofDays(1));

        assertEquals(Duration.ofSeconds(86_400), request.expires());
        assertEquals(0, request.startPriority());
        assertEquals(0, request.endPriority());
        assertEquals(Set.of(), request.methods());
        assertNull(request.type());
        assertNull(request.startTime());
        assertNull(request.endTime());
    }

    @Test
    void copiesMethodSetAndExposesItAsImmutable() {
        var methods = new HashSet<>(Set.of(7, 1, 3));
        var request = new GbAlarmSubscriptionRequest(Duration.ofSeconds(60), 1, 4,
                methods, 9, START, END);

        methods.clear();
        assertEquals(Set.of(1, 3, 7), request.methods());
        assertThrows(UnsupportedOperationException.class, () -> request.methods().add(2));
    }

    @Test
    void acceptsDocumentedBoundaryValues() {
        assertEquals(Duration.ofSeconds(1), GbAlarmSubscriptionRequest.all(Duration.ofSeconds(1)).expires());
        var request = new GbAlarmSubscriptionRequest(Duration.ofSeconds(86_400), 1, 4,
                Set.of(1, 2, 3, 4, 5, 6, 7), 1,
                LocalDateTime.of(1, 1, 1, 0, 0),
                LocalDateTime.of(9999, 12, 31, 23, 59, 59));
        assertEquals(1, request.startPriority());
        assertEquals(4, request.endPriority());
    }

    @Test
    void rejectsMissingOrInvalidDuration() {
        assertThrows(NullPointerException.class, () -> GbAlarmSubscriptionRequest.all(null));
        for (Duration expires : Set.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(86_401),
                Duration.ofSeconds(1).plusNanos(1))) {
            assertThrows(IllegalArgumentException.class, () -> GbAlarmSubscriptionRequest.all(expires), expires.toString());
        }
    }

    @Test
    void rejectsInvalidPriorityRanges() {
        for (int[] range : new int[][]{{0, 1}, {1, 0}, {-1, 0}, {1, 5}, {4, 3}}) {
            assertThrows(IllegalArgumentException.class, () -> request(range[0], range[1], Set.of(), null, null, null));
        }
    }

    @Test
    void rejectsMissingOrInvalidMethodsAndType() {
        assertThrows(NullPointerException.class, () -> request(0, 0, null, null, null, null));
        assertThrows(NullPointerException.class, () -> request(0, 0, setWithNull(), null, null, null));
        for (int method : new int[]{0, 8}) {
            assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(method), null, null, null));
        }
        assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(), 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(), -1, null, null));
    }

    @Test
    void rejectsUnpairedUnorderedFractionalAndUnsupportedYearTimes() {
        assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(), null, START, null));
        assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(), null, null, END));
        assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(), null, START, START));
        assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(), null, END, START));
        assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(), null, START.plusNanos(1), END));
        assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(), null, START, END.plusNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(), null, START.withYear(0), END));
        assertThrows(IllegalArgumentException.class, () -> request(0, 0, Set.of(), null, START, END.withYear(10000)));
    }

    private static GbAlarmSubscriptionRequest request(int startPriority, int endPriority, Set<Integer> methods,
                                                      Integer type, LocalDateTime startTime, LocalDateTime endTime) {
        return new GbAlarmSubscriptionRequest(Duration.ofMinutes(5), startPriority, endPriority,
                methods, type, startTime, endTime);
    }

    private static Set<Integer> setWithNull() {
        var methods = new HashSet<Integer>();
        methods.add(null);
        return methods;
    }
}

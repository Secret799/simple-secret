package com.ss.common.toolbox.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link LocalDateTimeRanges} 单元测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class LocalDateTimeRangesTest {

    @Test
    void shouldSplitClosedRangeWithoutGapOrOverlap() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 15, 12, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 2, 8, 30);

        List<LocalDateTimeRange> ranges = LocalDateTimeRanges.split(start, end, DateTimeUnit.MONTH);

        assertEquals(3, ranges.size());
        assertEquals(new LocalDateTimeRange(start,
                LocalDateTime.of(2026, 1, 31, 23, 59, 59, 999_999_999)), ranges.get(0));
        assertEquals(new LocalDateTimeRange(LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDateTime.of(2026, 2, 28, 23, 59, 59, 999_999_999)), ranges.get(1));
        assertEquals(new LocalDateTimeRange(LocalDateTime.of(2026, 3, 1, 0, 0), end), ranges.get(2));
        assertEquals(ranges.get(0).endInclusive().plusNanos(1L), ranges.get(1).startInclusive());
    }

    @Test
    void shouldReturnSingleRangeWhenBothEndpointsAreInSameUnit() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 13, 9, 0);
        LocalDateTime end = start.plusMinutes(30L);

        assertEquals(List.of(new LocalDateTimeRange(start, end)),
                LocalDateTimeRanges.split(start, end, DateTimeUnit.DAY));
    }

    @Test
    void shouldRejectInvalidRangeAndExcessiveSegmentCount() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = start.plusHours(3L);

        assertThrows(IllegalArgumentException.class, () -> new LocalDateTimeRange(end, start));
        assertThrows(IllegalArgumentException.class,
                () -> LocalDateTimeRanges.split(start, end, DateTimeUnit.HOUR, 3));
        assertThrows(IllegalArgumentException.class,
                () -> LocalDateTimeRanges.split(start, end, DateTimeUnit.HOUR, 0));
    }

    @Test
    void shouldSplitContinuouslyThroughLocalDateTimeMaximum() {
        LocalDateTime start = LocalDateTime.MAX.minusHours(1L);

        List<LocalDateTimeRange> ranges =
                LocalDateTimeRanges.split(start, LocalDateTime.MAX, DateTimeUnit.HOUR);

        assertEquals(2, ranges.size());
        assertEquals(start, ranges.get(0).startInclusive());
        assertEquals(ranges.get(0).endInclusive().plusNanos(1L), ranges.get(1).startInclusive());
        assertEquals(LocalDateTime.MAX, ranges.get(1).endInclusive());
    }
}

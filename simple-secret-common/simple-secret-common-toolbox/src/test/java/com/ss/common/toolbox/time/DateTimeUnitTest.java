package com.ss.common.toolbox.time;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DateTimeUnit} 单元测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class DateTimeUnitTest {

    @Test
    void shouldCalculateLeapYearAndQuarterBoundaries() {
        LocalDateTime leapDay = LocalDateTime.of(2024, 2, 29, 12, 30);
        LocalDateTime quarterDate = LocalDateTime.of(2026, 5, 10, 8, 15);

        assertEquals(LocalDateTime.of(2024, 2, 1, 0, 0), DateTimeUnit.MONTH.startOf(leapDay));
        assertEquals(LocalDateTime.of(2024, 2, 29, 23, 59, 59, 999_999_999),
                DateTimeUnit.MONTH.endOf(leapDay));
        assertEquals(LocalDateTime.of(2026, 4, 1, 0, 0), DateTimeUnit.QUARTER.startOf(quarterDate));
        assertEquals(LocalDateTime.of(2026, 6, 30, 23, 59, 59, 999_999_999),
                DateTimeUnit.QUARTER.endOf(quarterDate));
    }

    @Test
    void shouldUseMondayAsStartOfWeekAcrossYearBoundary() {
        LocalDateTime newYear = LocalDateTime.of(2026, 1, 1, 10, 0);

        assertEquals(LocalDateTime.of(2025, 12, 29, 0, 0), DateTimeUnit.WEEK.startOf(newYear));
        assertEquals(LocalDateTime.of(2026, 1, 4, 23, 59, 59, 999_999_999),
                DateTimeUnit.WEEK.endOf(newYear));
    }

    @Test
    void shouldResolveCodeAndRejectUnknownCode() {
        assertEquals(DateTimeUnit.HOUR, DateTimeUnit.fromCode("hour"));
        assertThrows(IllegalArgumentException.class, () -> DateTimeUnit.fromCode(null));
        assertThrows(IllegalArgumentException.class, () -> DateTimeUnit.fromCode("minutes"));
    }

    @Test
    void shouldSaturateNaturalBoundariesAtLocalDateTimeLimits() {
        for (DateTimeUnit unit : DateTimeUnit.values()) {
            assertEquals(LocalDateTime.MIN, unit.startOf(LocalDateTime.MIN));
            assertEquals(LocalDateTime.MAX, unit.endOf(LocalDateTime.MAX));
        }
    }
}

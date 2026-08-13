package com.ss.common.toolbox.time;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DurationUtils} 单元测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class DurationUtilsTest {

    @Test
    void shouldParseSupportedShortUnits() {
        assertEquals(Duration.ofSeconds(2L), DurationUtils.parse("2s"));
        assertEquals(Duration.ofMinutes(3L), DurationUtils.parse("3m"));
        assertEquals(Duration.ofHours(4L), DurationUtils.parse("4h"));
        assertEquals(Duration.ofDays(5L), DurationUtils.parse("5d"));
    }

    @Test
    void shouldRejectInvalidDurationText() {
        assertThrows(IllegalArgumentException.class, () -> DurationUtils.parse(null));
        assertThrows(IllegalArgumentException.class, () -> DurationUtils.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DurationUtils.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> DurationUtils.parse("s"));
        assertThrows(IllegalArgumentException.class, () -> DurationUtils.parse("2w"));
        assertThrows(IllegalArgumentException.class, () -> DurationUtils.parse("2 s"));
        assertThrows(IllegalArgumentException.class,
                () -> DurationUtils.parse(Long.MAX_VALUE + "d"));
    }
}

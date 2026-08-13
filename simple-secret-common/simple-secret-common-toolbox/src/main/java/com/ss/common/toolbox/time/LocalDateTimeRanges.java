package com.ss.common.toolbox.time;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 本地日期时间闭区间分段工具。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public final class LocalDateTimeRanges {

    /**
     * 默认允许生成的最大分段数量。
     */
    public static final int DEFAULT_MAX_SEGMENTS = 10_000;

    private LocalDateTimeRanges() {
    }

    /**
     * 按自然时间单位拆分闭区间。
     *
     * @param startInclusive 闭区间起点
     * @param endInclusive   闭区间终点
     * @param unit           分段单位
     * @return 连续且互不重叠的闭区间列表
     */
    public static List<LocalDateTimeRange> split(LocalDateTime startInclusive,
                                                  LocalDateTime endInclusive,
                                                  DateTimeUnit unit) {
        return split(startInclusive, endInclusive, unit, DEFAULT_MAX_SEGMENTS);
    }

    /**
     * 按自然时间单位拆分闭区间，并限制结果数量。
     *
     * @param startInclusive 闭区间起点
     * @param endInclusive   闭区间终点
     * @param unit           分段单位
     * @param maxSegments    允许生成的最大分段数量
     * @return 连续且互不重叠的闭区间列表
     */
    public static List<LocalDateTimeRange> split(LocalDateTime startInclusive,
                                                  LocalDateTime endInclusive,
                                                  DateTimeUnit unit,
                                                  int maxSegments) {
        LocalDateTimeRange source = new LocalDateTimeRange(startInclusive, endInclusive);
        Objects.requireNonNull(unit, "unit must not be null");
        if (maxSegments <= 0) {
            throw new IllegalArgumentException("maxSegments must be greater than zero");
        }
        return splitValidated(source, unit, maxSegments);
    }

    private static List<LocalDateTimeRange> splitValidated(LocalDateTimeRange source,
                                                            DateTimeUnit unit,
                                                            int maxSegments) {
        List<LocalDateTimeRange> ranges = new ArrayList<>();
        LocalDateTime cursor = source.startInclusive();
        while (!cursor.isAfter(source.endInclusive())) {
            ensureCapacity(ranges.size(), maxSegments);
            LocalDateTime unitEnd = unit.endOf(cursor);
            LocalDateTime segmentEnd = unitEnd.isBefore(source.endInclusive())
                    ? unitEnd : source.endInclusive();
            ranges.add(new LocalDateTimeRange(cursor, segmentEnd));
            if (segmentEnd.equals(source.endInclusive())) {
                break;
            }
            cursor = segmentEnd.plusNanos(1L);
        }
        return List.copyOf(ranges);
    }

    private static void ensureCapacity(int currentSize, int maxSegments) {
        if (currentSize >= maxSegments) {
            throw new IllegalArgumentException("Date-time range exceeds maxSegments: " + maxSegments);
        }
    }
}

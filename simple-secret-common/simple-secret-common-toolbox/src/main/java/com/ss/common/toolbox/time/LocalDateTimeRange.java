package com.ss.common.toolbox.time;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 纳秒精度的本地日期时间闭区间。
 *
 * @param startInclusive 闭区间起点
 * @param endInclusive   闭区间终点
 * @author junpzx
 * @since 2026-08-13
 */
public record LocalDateTimeRange(LocalDateTime startInclusive, LocalDateTime endInclusive) {

    /**
     * 校验闭区间端点。
     *
     * @param startInclusive 闭区间起点
     * @param endInclusive   闭区间终点
     */
    public LocalDateTimeRange {
        Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        Objects.requireNonNull(endInclusive, "endInclusive must not be null");
        if (startInclusive.isAfter(endInclusive)) {
            throw new IllegalArgumentException("startInclusive must not be after endInclusive");
        }
    }
}

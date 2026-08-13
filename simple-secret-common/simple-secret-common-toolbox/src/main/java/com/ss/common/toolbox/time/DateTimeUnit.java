package com.ss.common.toolbox.time;

import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 支持自然时间边界计算的时间单位。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public enum DateTimeUnit {

    /** 年。 */
    YEAR("year"),

    /** 季度。 */
    QUARTER("quarter"),

    /** 月。 */
    MONTH("month"),

    /** ISO 周，周一为每周第一天。 */
    WEEK("week"),

    /** 日。 */
    DAY("day"),

    /** 小时。 */
    HOUR("hour");

    /**
     * 时间单位编码。
     */
    private final String code;

    DateTimeUnit(String code) {
        this.code = code;
    }

    /**
     * 获取稳定的时间单位编码。
     *
     * @return 时间单位编码
     */
    public String code() {
        return code;
    }

    /**
     * 根据编码获取时间单位。
     *
     * @param code 时间单位编码
     * @return 匹配的时间单位
     * @throws IllegalArgumentException 当编码为空或不受支持时抛出
     */
    public static DateTimeUnit fromCode(String code) {
        for (DateTimeUnit unit : values()) {
            if (unit.code.equals(code)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("Unsupported date-time unit code: " + code);
    }

    /**
     * 计算指定时间所在自然单位的起点。
     *
     * @param dateTime 基准时间
     * @return 自然单位起点
     */
    public LocalDateTime startOf(LocalDateTime dateTime) {
        try {
            return switch (this) {
                case YEAR -> dateTime.withDayOfYear(1).toLocalDate().atStartOfDay();
                case QUARTER -> startOfQuarter(dateTime);
                case MONTH -> dateTime.withDayOfMonth(1).toLocalDate().atStartOfDay();
                case WEEK -> dateTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .toLocalDate().atStartOfDay();
                case DAY -> dateTime.toLocalDate().atStartOfDay();
                case HOUR -> dateTime.withMinute(0).withSecond(0).withNano(0);
            };
        } catch (DateTimeException exception) {
            if (dateTime.getYear() == LocalDateTime.MIN.getYear()) {
                return LocalDateTime.MIN;
            }
            throw exception;
        }
    }

    /**
     * 计算指定时间所在自然单位的闭区间终点。
     *
     * @param dateTime 基准时间
     * @return 自然单位闭区间终点；下一自然边界超过 Java 表示范围时返回
     *         {@link LocalDateTime#MAX}
     */
    public LocalDateTime endOf(LocalDateTime dateTime) {
        try {
            return nextStart(startOf(dateTime)).minusNanos(1L);
        } catch (DateTimeException exception) {
            if (dateTime.getYear() == LocalDateTime.MAX.getYear()) {
                return LocalDateTime.MAX;
            }
            throw exception;
        }
    }

    LocalDateTime nextStart(LocalDateTime start) {
        return switch (this) {
            case YEAR -> start.plusYears(1L);
            case QUARTER -> start.plusMonths(3L);
            case MONTH -> start.plusMonths(1L);
            case WEEK -> start.plusWeeks(1L);
            case DAY -> start.plusDays(1L);
            case HOUR -> start.plusHours(1L);
        };
    }

    private static LocalDateTime startOfQuarter(LocalDateTime dateTime) {
        int firstMonth = ((dateTime.getMonthValue() - 1) / 3) * 3 + 1;
        return dateTime.withMonth(firstMonth).withDayOfMonth(1).toLocalDate().atStartOfDay();
    }
}

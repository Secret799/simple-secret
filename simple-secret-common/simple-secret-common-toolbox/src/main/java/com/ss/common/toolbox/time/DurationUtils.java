package com.ss.common.toolbox.time;

import java.time.Duration;

/**
 * 短格式 {@link Duration} 解析工具。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public final class DurationUtils {

    private DurationUtils() {
    }

    /**
     * 解析由整数和单位组成的时间长度，支持 {@code s}、{@code m}、{@code h} 和 {@code d}。
     *
     * @param text 时间长度文本，例如 {@code 30s} 或 {@code 2h}
     * @return 解析后的时间长度
     * @throws IllegalArgumentException 当文本为空、数字无效或单位不受支持时抛出
     */
    public static Duration parse(String text) {
        if (text == null || text.length() < 2) {
            throw new IllegalArgumentException("Duration text must contain a number and a unit");
        }
        char unit = text.charAt(text.length() - 1);
        long value = parseValue(text.substring(0, text.length() - 1));
        try {
            return switch (unit) {
                case 's' -> Duration.ofSeconds(value);
                case 'm' -> Duration.ofMinutes(value);
                case 'h' -> Duration.ofHours(value);
                case 'd' -> Duration.ofDays(value);
                default -> throw new IllegalArgumentException("Unsupported duration unit: " + unit);
            };
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Duration value is out of range: " + text, exception);
        }
    }

    private static long parseValue(String valueText) {
        try {
            return Long.parseLong(valueText);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid duration value: " + valueText, exception);
        }
    }
}

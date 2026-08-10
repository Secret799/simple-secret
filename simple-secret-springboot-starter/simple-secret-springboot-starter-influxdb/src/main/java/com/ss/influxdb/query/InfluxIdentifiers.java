package com.ss.influxdb.query;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * InfluxQL 标识符、函数、时间间隔和值字面量的集中校验与转义工具。
 */
public final class InfluxIdentifiers {
    private static final Pattern FUNCTION = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern DURATION = Pattern.compile("(?:[1-9][0-9]*(?:ns|u|µ|ms|s|m|h|d|w))+");

    private InfluxIdentifiers() {
    }

    /**
     * 使用双引号包裹 InfluxQL 标识符，并转义反斜杠和双引号。
     *
     * @param identifier 标识符
     * @return 可安全拼入 InfluxQL 的标识符
     */
    public static String quote(String identifier) {
        String value = identifier(identifier);
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * 校验并规范化标识符，但不添加引号。
     *
     * @param identifier 标识符
     * @return 去除首尾空白后的标识符
     */
    public static String identifier(String identifier) {
        return requireText(identifier, "identifier");
    }

    /**
     * 校验函数名，仅允许普通函数标识符。
     *
     * @param function 函数名
     * @return 校验后的函数名
     */
    public static String function(String function) {
        String value = requireText(function, "function");
        if (!FUNCTION.matcher(value).matches()) {
            throw new IllegalArgumentException("InfluxQL function name is invalid");
        }
        return value;
    }

    /**
     * 校验 InfluxDB duration，例如 {@code 5m}、{@code 1h30m}。
     *
     * @param duration 时间间隔
     * @return 校验后的时间间隔
     */
    public static String duration(String duration) {
        String value = requireText(duration, "duration");
        if (!DURATION.matcher(value).matches()) {
            throw new IllegalArgumentException("InfluxDB duration is invalid");
        }
        return value;
    }

    /**
     * 将受支持的 Java 标量值渲染为 InfluxQL 字面量。
     *
     * @param value Java 值
     * @return 可安全拼入 InfluxQL 的字面量
     */
    public static String literal(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("InfluxQL condition value must not be null");
        }
        if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            return stringLiteral(value.toString());
        }
        if (value instanceof Boolean bool) {
            return bool.toString();
        }
        if (value instanceof Number number) {
            return numberLiteral(number);
        }
        if (value instanceof Instant instant) {
            return stringLiteral(instant.toString());
        }
        if (value instanceof LocalDateTime localDateTime) {
            return stringLiteral(localDateTime.toInstant(ZoneOffset.UTC).toString());
        }
        if (value instanceof Date date) {
            return stringLiteral(date.toInstant().toString());
        }
        throw new IllegalArgumentException("InfluxQL condition value type is unsupported");
    }

    private static String numberLiteral(Number value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof BigInteger || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return value.toString();
        }
        if (value instanceof Double doubleValue) {
            if (!Double.isFinite(doubleValue)) {
                throw new IllegalArgumentException("InfluxQL numeric value must be finite");
            }
            return Double.toString(doubleValue);
        }
        if (value instanceof Float floatValue) {
            if (!Float.isFinite(floatValue)) {
                throw new IllegalArgumentException("InfluxQL numeric value must be finite");
            }
            return Float.toString(floatValue);
        }
        throw new IllegalArgumentException("InfluxQL numeric value type is unsupported");
    }

    private static String stringLiteral(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank() || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "InfluxQL " + name + " must be non-blank and contain no control characters");
        }
        return value.trim();
    }
}

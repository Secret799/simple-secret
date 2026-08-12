package com.ss.influxdb.mapping;

import org.influxdb.dto.Point;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 将 InfluxDB 注解实体转换为客户端 {@link Point}。
 */
public class InfluxPointMapper {
    private final InfluxMetadataRegistry registry;

    /**
     * 创建实体到 Point 的映射器。
     *
     * @param registry 组件注册表
     */
    public InfluxPointMapper(InfluxMetadataRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * 使用实体注解 measurement 创建 Point。
     *
     * @param entity 实体对象
     * @return 返回的 {@code Point} 结果
     */
    public Point toPoint(Object entity) {
        Objects.requireNonNull(entity, "entity");
        InfluxEntityMetadata metadata = registry.metadata(entity.getClass());
        Point.Builder builder = Point.measurement(metadata.getMeasurementName());
        for (InfluxFieldMetadata field : metadata.getFields()) {
            Object value = field.read(entity);
            if (value == null) {
                continue;
            }
            if (field.isTime()) {
                builder.time(toTimestamp(value, field.getTimeUnit()), field.getTimeUnit());
            } else if (field.isTag()) {
                builder.tag(field.getColumnName(), String.valueOf(value));
            } else {
                addField(builder, field.getColumnName(), value);
            }
        }
        if (!builder.hasFields()) {
            throw new IllegalArgumentException("InfluxDB point requires at least one non-null field");
        }
        return builder.build();
    }

    private static Number toTimestamp(Object value, TimeUnit targetUnit) {
        if (value instanceof Number number) {
            return exactLong(number, "InfluxDB numeric time must be a finite integer");
        }
        Instant instant;
        if (value instanceof Instant item) {
            instant = item;
        } else if (value instanceof LocalDateTime item) {
            instant = item.toInstant(ZoneOffset.UTC);
        } else if (value instanceof Date item) {
            instant = item.toInstant();
        } else {
            throw new IllegalArgumentException("Unsupported InfluxDB time value type");
        }
        long seconds = targetUnit.convert(instant.getEpochSecond(), TimeUnit.SECONDS);
        long nanos = targetUnit.convert(instant.getNano(), TimeUnit.NANOSECONDS);
        return Math.addExact(seconds, nanos);
    }

    private static void addField(Point.Builder builder, String name, Object value) {
        if (value instanceof Number number) {
            requireFinite(number, "InfluxDB numeric field must be finite");
            builder.addField(name, number);
        } else if (value instanceof Boolean bool) {
            builder.addField(name, bool);
        } else if (value instanceof Enum<?> item) {
            builder.addField(name, item.name());
        } else {
            builder.addField(name, String.valueOf(value));
        }
    }

    private static long exactLong(Number value, String message) {
        try {
            if (value instanceof BigDecimal decimal) {
                return decimal.longValueExact();
            }
            if (value instanceof BigInteger integer) {
                return integer.longValueExact();
            }
            if (value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long) {
                return value.longValue();
            }
            if (value instanceof Double doubleValue) {
                requireFinite(doubleValue, message);
                return BigDecimal.valueOf(doubleValue).longValueExact();
            }
            if (value instanceof Float floatValue) {
                requireFinite(floatValue, message);
                return new BigDecimal(Float.toString(floatValue)).longValueExact();
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(message, exception);
        }
        throw new IllegalArgumentException("InfluxDB numeric time type is unsupported");
    }

    private static void requireFinite(Number value, String message) {
        if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)
                || value instanceof Float floatValue && !Float.isFinite(floatValue)) {
            throw new IllegalArgumentException(message);
        }
    }
}

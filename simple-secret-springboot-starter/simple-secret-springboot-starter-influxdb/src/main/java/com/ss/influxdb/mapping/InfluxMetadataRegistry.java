package com.ss.influxdb.mapping;

import com.ss.common.toolbox.function.SerializableFunction;
import com.ss.common.toolbox.property.LambdaPropertyResolver;
import org.influxdb.annotation.Column;
import org.influxdb.annotation.Exclude;
import org.influxdb.annotation.Measurement;
import org.influxdb.annotation.TimeColumn;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 创建并缓存 InfluxDB 注解实体映射。
 */
public class InfluxMetadataRegistry {
    private static final String UNASSIGNED_DATABASE = "[unassigned]";
    private static final String DEFAULT_RETENTION_POLICY = "autogen";

    private final ConcurrentMap<Class<?>, InfluxEntityMetadata> cache = new ConcurrentHashMap<>();

    /** 获取实体元数据，不存在时原子创建。 */
    public InfluxEntityMetadata metadata(Class<?> entityType) {
        if (entityType == null || entityType.isPrimitive() || entityType.isInterface()
                || entityType.isArray() || entityType.isEnum()) {
            throw new IllegalArgumentException("InfluxDB entity type must be a concrete class");
        }
        return cache.computeIfAbsent(entityType, this::createMetadata);
    }

    /** 解析 getter 对应的 InfluxDB 列名。 */
    public <T, R> String column(SerializableFunction<T, R> getter) {
        Field field = LambdaPropertyResolver.resolveField(getter);
        TimeColumn timeColumn = field.getAnnotation(TimeColumn.class);
        if (timeColumn != null) {
            return "time";
        }
        Column column = field.getAnnotation(Column.class);
        if (column != null) {
            return isBlank(column.name()) ? field.getName() : requireName(column.name(), "column");
        }
        Measurement measurement = field.getDeclaringClass().getAnnotation(Measurement.class);
        if (measurement != null && measurement.allFields()) {
            return requireName(field.getName(), "column");
        }
        throw new IllegalArgumentException("Getter field is not mapped to InfluxDB: " + field.getName());
    }

    /**
     * 按目标实体元数据解析 getter 对应的 InfluxDB 列名。
     *
     * @param entityType 查询实体类型
     * @param getter     实体 getter
     * @param <T>        实体类型
     * @param <R>        getter 返回类型
     * @return 已映射的 InfluxDB 列名
     */
    public <T, R> String column(Class<T> entityType, SerializableFunction<T, R> getter) {
        Field field = LambdaPropertyResolver.resolveField(getter);
        if (!field.getDeclaringClass().isAssignableFrom(entityType)) {
            throw new IllegalArgumentException("Getter does not belong to the query entity hierarchy");
        }
        InfluxFieldMetadata mappedField = metadata(entityType).fieldByProperty(field.getName());
        if (mappedField == null) {
            throw new IllegalArgumentException(
                    "Getter field is not mapped to InfluxDB: " + field.getName());
        }
        return mappedField.getColumnName();
    }

    /** 移除指定实体的缓存，主要用于开发期动态类型和测试。 */
    public void remove(Class<?> entityType) {
        cache.remove(entityType);
    }

    private InfluxEntityMetadata createMetadata(Class<?> entityType) {
        Measurement measurement = entityType.getAnnotation(Measurement.class);
        String measurementName = measurement == null || isBlank(measurement.name())
                ? entityType.getSimpleName() : measurement.name();
        measurementName = requireName(measurementName, "measurement");
        String database = measurement == null ? null
                : normalizeOverride(measurement.database(), UNASSIGNED_DATABASE, "database");
        String retentionPolicy = measurement == null ? null
                : normalizeOverride(measurement.retentionPolicy(), DEFAULT_RETENTION_POLICY, "retention policy");
        boolean allFields = measurement != null && measurement.allFields();

        List<InfluxFieldMetadata> fields = new ArrayList<>();
        Set<String> columns = new HashSet<>();
        boolean hasTime = false;
        for (Class<?> type : hierarchy(entityType)) {
            for (Field field : type.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())
                        || Modifier.isTransient(field.getModifiers())
                        || field.isAnnotationPresent(Exclude.class)) {
                    continue;
                }
                TimeColumn timeColumn = field.getAnnotation(TimeColumn.class);
                Column column = field.getAnnotation(Column.class);
                if (timeColumn == null && column == null && !allFields) {
                    continue;
                }
                boolean time = timeColumn != null;
                if (time && hasTime) {
                    throw new IllegalArgumentException("InfluxDB entity contains more than one time field");
                }
                String columnName = time ? "time"
                        : requireName(column == null || isBlank(column.name())
                        ? field.getName() : column.name(), "column");
                if (!columns.add(columnName)) {
                    throw new IllegalArgumentException("InfluxDB entity contains duplicate column: " + columnName);
                }
                validateType(field, time, column != null && column.tag());
                fields.add(new InfluxFieldMetadata(field, columnName,
                        !time && column != null && column.tag(), time,
                        time ? timeColumn.timeUnit() : null));
                hasTime |= time;
            }
        }
        if (fields.stream().noneMatch(field -> !field.isTime())) {
            throw new IllegalArgumentException("InfluxDB entity requires at least one column field");
        }
        return new InfluxEntityMetadata(entityType, measurementName, database, retentionPolicy, fields);
    }

    private static List<Class<?>> hierarchy(Class<?> entityType) {
        List<Class<?>> result = new ArrayList<>();
        Class<?> current = entityType;
        while (current != null && current != Object.class) {
            result.add(0, current);
            current = current.getSuperclass();
        }
        return result;
    }

    private static void validateType(Field field, boolean time, boolean tag) {
        Class<?> type = field.getType();
        boolean supported;
        if (time) {
            supported = isNumeric(type) || type == Instant.class || type == LocalDateTime.class
                    || Date.class.isAssignableFrom(type);
        } else if (tag) {
            supported = isScalar(type);
        } else {
            supported = isScalar(type) && type != Character.class && type != Character.TYPE;
        }
        if (!supported) {
            throw new IllegalArgumentException("InfluxDB field has unsupported type: " + field.getName());
        }
    }

    private static boolean isScalar(Class<?> type) {
        return type == String.class || type == Boolean.class || type == Boolean.TYPE
                || type == Character.class || type == Character.TYPE || type.isEnum() || isNumeric(type);
    }

    private static boolean isNumeric(Class<?> type) {
        return type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                || type == Float.class || type == Double.class || type == BigInteger.class
                || type == BigDecimal.class || type == Byte.TYPE || type == Short.TYPE
                || type == Integer.TYPE || type == Long.TYPE || type == Float.TYPE || type == Double.TYPE;
    }

    private static String normalizeOverride(String value, String sentinel, String name) {
        if (isBlank(value) || sentinel.equals(value)) {
            return null;
        }
        return requireName(value, name);
    }

    private static String requireName(String value, String name) {
        if (isBlank(value) || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    "InfluxDB " + name + " must be non-blank and contain no control characters");
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.ss.influxdb.mapping;

import com.ss.influxdb.exception.InfluxOperationException;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 单个 InfluxDB 实体字段的不可变映射信息。
 */
public final class InfluxFieldMetadata {
    private final Field field;
    private final String propertyName;
    private final String columnName;
    private final Class<?> type;
    private final boolean tag;
    private final boolean time;
    private final TimeUnit timeUnit;

    InfluxFieldMetadata(Field field, String columnName, boolean tag, boolean time, TimeUnit timeUnit) {
        this.field = Objects.requireNonNull(field, "field");
        this.propertyName = field.getName();
        this.columnName = Objects.requireNonNull(columnName, "columnName");
        this.type = field.getType();
        this.tag = tag;
        this.time = time;
        this.timeUnit = timeUnit;
        if (!field.trySetAccessible()) {
            throw new IllegalArgumentException("InfluxDB field is not accessible: " + field.getName());
        }
    }

    /** @return Java 反射字段 */ public Field getField() { return field; }
    /** @return Java 属性名 */ public String getPropertyName() { return propertyName; }
    /** @return InfluxDB 列名 */ public String getColumnName() { return columnName; }
    /** @return Java 字段类型 */ public Class<?> getType() { return type; }
    /** @return 是否为 tag */ public boolean isTag() { return tag; }
    /** @return 是否为时间列 */ public boolean isTime() { return time; }
    /** @return 时间精度；非时间列为 {@code null} */ public TimeUnit getTimeUnit() { return timeUnit; }

    /** 读取实体字段值。 */
    public Object read(Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException | RuntimeException exception) {
            throw new InfluxOperationException("Unable to read InfluxDB field " + propertyName, exception);
        }
    }

    /** 写入实体字段值。 */
    public void write(Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException | RuntimeException exception) {
            throw new InfluxOperationException("Unable to write InfluxDB field " + propertyName, exception);
        }
    }
}

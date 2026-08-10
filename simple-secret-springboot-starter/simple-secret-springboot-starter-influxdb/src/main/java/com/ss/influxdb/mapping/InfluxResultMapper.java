package com.ss.influxdb.mapping;

import com.ss.influxdb.exception.InfluxOperationException;
import org.influxdb.dto.QueryResult;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 将 InfluxDB {@link QueryResult} 映射为注解实体。
 */
public class InfluxResultMapper {
    private final InfluxMetadataRegistry registry;

    /** 创建查询结果映射器。 */
    public InfluxResultMapper(InfluxMetadataRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /** 映射完整查询结果，服务端错误会转换为统一异常。 */
    public <T> List<T> map(QueryResult result, Class<T> entityType) {
        if (result == null) {
            return List.of();
        }
        if (result.hasError()) {
            throw new InfluxOperationException("InfluxDB query result contains a server error");
        }
        if (result.getResults() == null) {
            return List.of();
        }
        List<T> records = new ArrayList<>();
        for (QueryResult.Result item : result.getResults()) {
            if (item == null) {
                continue;
            }
            if (item.hasError()) {
                throw new InfluxOperationException("InfluxDB query result item contains a server error");
            }
            if (item.getSeries() != null) {
                for (QueryResult.Series series : item.getSeries()) {
                    records.addAll(map(series, entityType));
                }
            }
        }
        return List.copyOf(records);
    }

    /** 映射单个 series，包括 group by 返回的 tags。 */
    public <T> List<T> map(QueryResult.Series series, Class<T> entityType) {
        if (series == null || series.getValues() == null || series.getValues().isEmpty()) {
            return List.of();
        }
        InfluxEntityMetadata metadata = registry.metadata(entityType);
        List<String> columns = series.getColumns() == null ? List.of() : series.getColumns();
        Map<String, String> tags = series.getTags() == null ? Collections.emptyMap() : series.getTags();
        Constructor<T> constructor = constructor(entityType);
        List<T> records = new ArrayList<>(series.getValues().size());
        for (List<Object> row : series.getValues()) {
            T target = instantiate(constructor);
            Set<String> writtenColumns = new HashSet<>();
            int count = Math.min(columns.size(), row == null ? 0 : row.size());
            for (int index = 0; index < count; index++) {
                String column = columns.get(index);
                if (write(metadata, target, column, row.get(index))) {
                    writtenColumns.add(column);
                }
            }
            for (Map.Entry<String, String> tag : tags.entrySet()) {
                InfluxFieldMetadata field = metadata.fieldByColumn(tag.getKey());
                if (field != null && !writtenColumns.contains(tag.getKey())) {
                    write(field, target, tag.getValue());
                }
            }
            records.add(target);
        }
        return List.copyOf(records);
    }

    private static <T> Constructor<T> constructor(Class<T> entityType) {
        try {
            Constructor<T> constructor = entityType.getDeclaredConstructor();
            if (!constructor.trySetAccessible()) {
                throw new InfluxOperationException("InfluxDB entity constructor is not accessible");
            }
            return constructor;
        } catch (NoSuchMethodException exception) {
            throw new InfluxOperationException("InfluxDB entity requires a no-argument constructor", exception);
        }
    }

    private static <T> T instantiate(Constructor<T> constructor) {
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            throw new InfluxOperationException("Unable to instantiate InfluxDB entity", exception);
        }
    }

    private static boolean write(InfluxEntityMetadata metadata, Object target, String column, Object value) {
        InfluxFieldMetadata field = metadata.fieldByColumn(column);
        if (field == null || value == null) {
            return false;
        }
        write(field, target, value);
        return true;
    }

    private static void write(InfluxFieldMetadata field, Object target, Object value) {
        if (value == null) {
            return;
        }
        try {
            field.write(target, convert(field, value));
        } catch (InfluxOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InfluxOperationException(
                    "Unable to convert InfluxDB column " + field.getColumnName(), exception);
        }
    }

    private static Object convert(InfluxFieldMetadata field, Object value) {
        if (field.isTime()) {
            return convertTime(field, value);
        }
        Class<?> type = wrap(field.getType());
        if (type.isInstance(value)) {
            return value;
        }
        if (Number.class.isAssignableFrom(type)) {
            return convertNumber(type, value);
        }
        if (type == Boolean.class) {
            return convertBoolean(value);
        }
        if (type == Character.class) {
            return convertCharacter(value);
        }
        if (type == String.class) {
            return value.toString();
        }
        if (type.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object item = Enum.valueOf((Class<? extends Enum>) type, value.toString());
            return item;
        }
        return value;
    }

    private static Boolean convertBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = value.toString();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("InfluxDB boolean value must be true or false");
    }

    private static Character convertCharacter(Object value) {
        if (value instanceof Character character) {
            return character;
        }
        String text = value.toString();
        if (text.length() != 1) {
            throw new IllegalArgumentException("InfluxDB character value must contain one character");
        }
        return text.charAt(0);
    }

    private static Object convertTime(InfluxFieldMetadata field, Object value) {
        Instant instant;
        if (value instanceof Instant item) {
            instant = item;
        } else if (value instanceof Number number) {
            long nanos = TimeUnit.NANOSECONDS.convert(number.longValue(), field.getTimeUnit());
            instant = Instant.ofEpochSecond(0, nanos);
        } else {
            instant = OffsetDateTime.parse(value.toString()).toInstant();
        }
        Class<?> type = wrap(field.getType());
        if (type == Instant.class) {
            return instant;
        }
        if (type == LocalDateTime.class) {
            return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (Date.class.isAssignableFrom(type)) {
            return Date.from(instant);
        }
        if (Number.class.isAssignableFrom(type)) {
            long seconds = field.getTimeUnit().convert(instant.getEpochSecond(), TimeUnit.SECONDS);
            long fraction = field.getTimeUnit().convert(instant.getNano(), TimeUnit.NANOSECONDS);
            return convertNumber(type, Math.addExact(seconds, fraction));
        }
        throw new IllegalArgumentException("Unsupported InfluxDB time target type");
    }

    private static Object convertNumber(Class<?> type, Object value) {
        BigDecimal decimal = value instanceof Number number
                ? new BigDecimal(number.toString()) : new BigDecimal(value.toString());
        if (type == Byte.class) return decimal.byteValueExact();
        if (type == Short.class) return decimal.shortValueExact();
        if (type == Integer.class) return decimal.intValueExact();
        if (type == Long.class) return decimal.longValueExact();
        if (type == Float.class) return decimal.floatValue();
        if (type == Double.class) return decimal.doubleValue();
        if (type == BigInteger.class) return decimal.toBigIntegerExact();
        if (type == BigDecimal.class) return decimal;
        throw new IllegalArgumentException("Unsupported InfluxDB numeric target type");
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        return Character.class;
    }
}

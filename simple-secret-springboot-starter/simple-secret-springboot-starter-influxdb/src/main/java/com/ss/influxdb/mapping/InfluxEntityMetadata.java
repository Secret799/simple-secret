package com.ss.influxdb.mapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * InfluxDB measurement 与实体列的不可变映射信息。
 */
public final class InfluxEntityMetadata {
    private final Class<?> entityType;
    private final String measurementName;
    private final String databaseName;
    private final String retentionPolicy;
    private final List<InfluxFieldMetadata> fields;
    private final Map<String, InfluxFieldMetadata> fieldsByProperty;
    private final Map<String, InfluxFieldMetadata> fieldsByColumn;
    private final InfluxFieldMetadata timeField;

    InfluxEntityMetadata(Class<?> entityType, String measurementName, String databaseName,
                         String retentionPolicy, List<InfluxFieldMetadata> fields) {
        this.entityType = entityType;
        this.measurementName = measurementName;
        this.databaseName = databaseName;
        this.retentionPolicy = retentionPolicy;
        this.fields = List.copyOf(fields);
        Map<String, InfluxFieldMetadata> propertyMap = new LinkedHashMap<>();
        Map<String, InfluxFieldMetadata> columnMap = new LinkedHashMap<>();
        InfluxFieldMetadata foundTime = null;
        for (InfluxFieldMetadata field : fields) {
            propertyMap.put(field.getPropertyName(), field);
            columnMap.put(field.getColumnName(), field);
            if (field.isTime()) {
                foundTime = field;
            }
        }
        fieldsByProperty = Map.copyOf(propertyMap);
        fieldsByColumn = Map.copyOf(columnMap);
        timeField = foundTime;
    }

    /** @return 实体类型 */ public Class<?> getEntityType() { return entityType; }
    /** @return measurement 名称 */ public String getMeasurementName() { return measurementName; }
    /** @return 注解数据库名；未指定时为 {@code null} */
    public String getDatabaseName() { return databaseName; }

    /** @return 注解 retention policy；未指定时为 {@code null} */
    public String getRetentionPolicy() { return retentionPolicy; }
    /** @return 所有已映射字段 */ public List<InfluxFieldMetadata> getFields() { return fields; }
    /** @return 可选时间字段 */
    public Optional<InfluxFieldMetadata> getTimeField() { return Optional.ofNullable(timeField); }

    /**
     * 按 Java 属性名查找字段。
     *
     * @param property Java 属性名
     * @return 字段元数据；不存在时为 {@code null}
     */
    public InfluxFieldMetadata fieldByProperty(String property) { return fieldsByProperty.get(property); }

    /**
     * 按 InfluxDB 列名查找字段。
     *
     * @param column InfluxDB 列名
     * @return 字段元数据；不存在时为 {@code null}
     */
    public InfluxFieldMetadata fieldByColumn(String column) { return fieldsByColumn.get(column); }
}

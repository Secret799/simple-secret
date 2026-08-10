package com.ss.influxdb.query;

import com.ss.common.toolbox.function.SerializableFunction;
import com.ss.influxdb.mapping.InfluxEntityMetadata;
import com.ss.influxdb.mapping.InfluxFieldMetadata;
import com.ss.influxdb.mapping.InfluxMetadataRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 基于实体 getter 构建安全 InfluxQL 查询，不接受任意原始条件片段。
 *
 * @param <T> InfluxDB 注解实体类型
 */
public final class LambdaQueryWrapper<T> {
    private final Class<T> entityType;
    private final InfluxMetadataRegistry registry;
    private final InfluxEntityMetadata metadata;
    private final List<String> selections = new ArrayList<>();
    private final List<ConditionEntry> conditions = new ArrayList<>();
    private final List<String> groups = new ArrayList<>();
    private String measurement;
    private String retentionPolicy;
    private String order;
    private Integer limit;
    private Integer offset;
    /** 当前查询是否包含 tag 分组。 */
    private boolean tagGrouping;
    /** limit 和 offset 是否由全局分页 API 设置。 */
    private boolean paginationRequested;

    private LambdaQueryWrapper(Class<T> entityType, InfluxMetadataRegistry registry) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.registry = Objects.requireNonNull(registry, "registry");
        metadata = registry.metadata(entityType);
        measurement = metadata.getMeasurementName();
        retentionPolicy = metadata.getRetentionPolicy();
    }

    private LambdaQueryWrapper(LambdaQueryWrapper<T> source) {
        entityType = source.entityType;
        registry = source.registry;
        metadata = source.metadata;
        selections.addAll(source.selections);
        conditions.addAll(source.conditions);
        groups.addAll(source.groups);
        measurement = source.measurement;
        retentionPolicy = source.retentionPolicy;
        order = source.order;
        limit = source.limit;
        offset = source.offset;
        tagGrouping = source.tagGrouping;
        paginationRequested = source.paginationRequested;
    }

    /** 创建指定实体的查询构建器。 */
    public static <T> LambdaQueryWrapper<T> of(Class<T> entityType, InfluxMetadataRegistry registry) {
        return new LambdaQueryWrapper<>(entityType, registry);
    }

    /** 选择一个或多个实体列；未调用时使用 {@code *}。 */
    @SafeVarargs
    public final LambdaQueryWrapper<T> select(SerializableFunction<T, ?>... getters) {
        requireGetters(getters);
        for (SerializableFunction<T, ?> getter : getters) {
            selections.add(column(getter));
        }
        return this;
    }

    /** 添加聚合或转换函数选择项。 */
    public LambdaQueryWrapper<T> function(String function, SerializableFunction<T, ?> getter, String alias) {
        selections.add(InfluxIdentifiers.function(function) + "(" + column(getter) + ") AS "
                + InfluxIdentifiers.quote(alias));
        return this;
    }

    /** 覆盖实体注解中的 measurement。 */
    public LambdaQueryWrapper<T> measurement(String measurement) {
        this.measurement = validatedIdentifier(measurement);
        return this;
    }

    /** 覆盖实体注解中的 retention policy。 */
    public LambdaQueryWrapper<T> retentionPolicy(String retentionPolicy) {
        this.retentionPolicy = validatedIdentifier(retentionPolicy);
        return this;
    }

    /** 添加等于条件。 */
    public LambdaQueryWrapper<T> eq(SerializableFunction<T, ?> getter, Object value) {
        return comparison(getter, "=", value);
    }

    /** 添加不等于条件。 */
    public LambdaQueryWrapper<T> ne(SerializableFunction<T, ?> getter, Object value) {
        return comparison(getter, "!=", value);
    }

    /** 添加大于条件。 */
    public LambdaQueryWrapper<T> gt(SerializableFunction<T, ?> getter, Object value) {
        return comparison(getter, ">", value);
    }

    /** 添加大于等于条件。 */
    public LambdaQueryWrapper<T> ge(SerializableFunction<T, ?> getter, Object value) {
        return comparison(getter, ">=", value);
    }

    /** 添加小于条件。 */
    public LambdaQueryWrapper<T> lt(SerializableFunction<T, ?> getter, Object value) {
        return comparison(getter, "<", value);
    }

    /** 添加小于等于条件。 */
    public LambdaQueryWrapper<T> le(SerializableFunction<T, ?> getter, Object value) {
        return comparison(getter, "<=", value);
    }

    /** 添加闭区间条件。 */
    public LambdaQueryWrapper<T> between(SerializableFunction<T, ?> getter, Object lower, Object upper) {
        String column = column(getter);
        add("AND", new GroupCondition(List.of(
                new ConditionEntry("AND", new BinaryCondition(column, ">=", InfluxIdentifiers.literal(lower))),
                new ConditionEntry("AND", new BinaryCondition(column, "<=", InfluxIdentifiers.literal(upper))))));
        return this;
    }

    /** 添加集合包含条件，内部使用 OR 连接。 */
    public LambdaQueryWrapper<T> in(SerializableFunction<T, ?> getter, Collection<?> values) {
        return collectionCondition(getter, values, "=", "OR");
    }

    /** 添加集合排除条件，内部使用 AND 连接。 */
    public LambdaQueryWrapper<T> notIn(SerializableFunction<T, ?> getter, Collection<?> values) {
        return collectionCondition(getter, values, "!=", "AND");
    }

    /** 添加一个由 AND 与外层条件连接的条件组。 */
    public LambdaQueryWrapper<T> and(Consumer<LambdaQueryWrapper<T>> consumer) {
        return nested("AND", consumer);
    }

    /** 添加一个由 OR 与外层条件连接的条件组。 */
    public LambdaQueryWrapper<T> or(Consumer<LambdaQueryWrapper<T>> consumer) {
        return nested("OR", consumer);
    }

    /** 添加一个或多个分组列。 */
    @SafeVarargs
    public final LambdaQueryWrapper<T> groupBy(SerializableFunction<T, ?>... getters) {
        requireGetters(getters);
        for (SerializableFunction<T, ?> getter : getters) {
            InfluxFieldMetadata field = mappedField(getter);
            if (!field.isTag()) {
                throw new IllegalArgumentException("InfluxQL GROUP BY requires a tag field");
            }
            groups.add(InfluxIdentifiers.quote(field.getColumnName()));
            tagGrouping = true;
        }
        return this;
    }

    /** 添加时间间隔分组。 */
    public LambdaQueryWrapper<T> groupByTime(String duration) {
        groups.add("time(" + InfluxIdentifiers.duration(duration) + ")");
        return this;
    }

    /** 按时间升序排列。 */
    public LambdaQueryWrapper<T> orderByTimeAsc() {
        order = "ASC";
        return this;
    }

    /** 按时间降序排列。 */
    public LambdaQueryWrapper<T> orderByTimeDesc() {
        order = "DESC";
        return this;
    }

    /** 限制返回记录数。 */
    public LambdaQueryWrapper<T> limit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("InfluxQL limit must be greater than zero");
        }
        this.limit = limit;
        return this;
    }

    /** 设置跳过的记录数。 */
    public LambdaQueryWrapper<T> offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("InfluxQL offset must not be negative");
        }
        this.offset = offset;
        return this;
    }

    /** 使用从 1 开始的页码设置 limit 与 offset。 */
    public LambdaQueryWrapper<T> page(int current, int pageSize) {
        if (current <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("InfluxQL page and page size must be greater than zero");
        }
        long calculatedOffset = (long) (current - 1) * pageSize;
        if (calculatedOffset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("InfluxQL page offset is too large");
        }
        limit = pageSize;
        offset = (int) calculatedOffset;
        paginationRequested = true;
        return this;
    }

    /** @return 查询实体类型 */
    public Class<T> getEntityType() {
        return entityType;
    }

    /** @return 实体注解中的数据库名；未指定时为 {@code null} */
    public String getDatabaseName() {
        return metadata.getDatabaseName();
    }

    /** 创建当前查询状态的独立副本。 */
    public LambdaQueryWrapper<T> copy() {
        return new LambdaQueryWrapper<>(this);
    }

    /**
     * 使用显式非空字段构建计数查询，不包含排序、limit 和 offset。
     *
     * <p>InfluxQL 的 count 只统计指定 field 的非空值，
     * 调用方应选择业务上始终非空的普通 field。</p>
     */
    public String buildCount(SerializableFunction<T, ?> countGetter) {
        InfluxFieldMetadata countField = mappedField(countGetter);
        if (countField.isTag() || countField.isTime()) {
            throw new IllegalArgumentException("InfluxDB count requires a non-tag field");
        }
        if (!groups.isEmpty()) {
            throw new IllegalArgumentException("InfluxDB pagination count does not support grouping");
        }
        StringBuilder query = new StringBuilder("SELECT count(")
                .append(InfluxIdentifiers.quote(countField.getColumnName()))
                .append(") AS \"ss_total\" FROM ");
        appendSource(query);
        if (!conditions.isEmpty()) {
            query.append(" WHERE ").append(renderEntries(conditions));
        }
        if (!groups.isEmpty()) {
            query.append(" GROUP BY ").append(String.join(", ", groups));
        }
        return query.toString();
    }

    /** 构建完整 InfluxQL 查询。 */
    public String build() {
        if (tagGrouping && paginationRequested) {
            throw new IllegalArgumentException("InfluxDB pagination does not support tag grouping");
        }
        StringBuilder query = new StringBuilder("SELECT ");
        query.append(selections.isEmpty() ? "*" : String.join(", ", selections));
        query.append(" FROM ");
        appendSource(query);
        if (!conditions.isEmpty()) {
            query.append(" WHERE ").append(renderEntries(conditions));
        }
        if (!groups.isEmpty()) {
            query.append(" GROUP BY ").append(String.join(", ", groups));
        }
        if (order != null) {
            query.append(" ORDER BY time ").append(order);
        }
        if (limit != null) {
            query.append(" LIMIT ").append(limit);
        }
        if (offset != null) {
            query.append(" OFFSET ").append(offset);
        }
        return query.toString();
    }

    private void appendSource(StringBuilder query) {
        if (retentionPolicy != null) {
            query.append(InfluxIdentifiers.quote(retentionPolicy)).append('.');
        }
        query.append(InfluxIdentifiers.quote(measurement));
    }

    private LambdaQueryWrapper<T> comparison(SerializableFunction<T, ?> getter, String operator, Object value) {
        add("AND", new BinaryCondition(column(getter), operator, InfluxIdentifiers.literal(value)));
        return this;
    }

    private LambdaQueryWrapper<T> collectionCondition(SerializableFunction<T, ?> getter, Collection<?> values,
                                                       String operator, String connector) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("InfluxQL condition values must not be empty");
        }
        String column = column(getter);
        List<ConditionEntry> entries = new ArrayList<>(values.size());
        for (Object value : values) {
            entries.add(new ConditionEntry(connector,
                    new BinaryCondition(column, operator, InfluxIdentifiers.literal(value))));
        }
        add("AND", new GroupCondition(entries));
        return this;
    }

    private LambdaQueryWrapper<T> nested(String connector, Consumer<LambdaQueryWrapper<T>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        LambdaQueryWrapper<T> nested = new LambdaQueryWrapper<>(entityType, registry);
        consumer.accept(nested);
        if (nested.conditions.isEmpty()) {
            throw new IllegalArgumentException("InfluxQL nested condition must not be empty");
        }
        add(connector, new GroupCondition(List.copyOf(nested.conditions)));
        return this;
    }

    private void add(String connector, Condition condition) {
        conditions.add(new ConditionEntry(connector, condition));
    }

    private String column(SerializableFunction<T, ?> getter) {
        return InfluxIdentifiers.quote(mappedField(getter).getColumnName());
    }

    private InfluxFieldMetadata mappedField(SerializableFunction<T, ?> getter) {
        Objects.requireNonNull(getter, "getter");
        String column = registry.column(entityType, getter);
        InfluxFieldMetadata field = metadata.fieldByColumn(column);
        if (field == null) {
            throw new IllegalArgumentException("Getter is not mapped by the query entity");
        }
        return field;
    }

    private static String validatedIdentifier(String identifier) {
        return InfluxIdentifiers.identifier(identifier);
    }

    private static void requireGetters(SerializableFunction<?, ?>[] getters) {
        if (getters == null || getters.length == 0) {
            throw new IllegalArgumentException("At least one InfluxDB getter is required");
        }
    }

    private static String renderEntries(List<ConditionEntry> entries) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < entries.size(); index++) {
            ConditionEntry entry = entries.get(index);
            if (index > 0) {
                result.append(' ').append(entry.connector()).append(' ');
            }
            result.append(entry.condition().render());
        }
        return result.toString();
    }

    private interface Condition {
        String render();
    }

    private record ConditionEntry(String connector, Condition condition) {
    }

    private record BinaryCondition(String column, String operator, String literal) implements Condition {
        @Override
        public String render() {
            return column + " " + operator + " " + literal;
        }
    }

    private record GroupCondition(List<ConditionEntry> entries) implements Condition {
        @Override
        public String render() {
            return "(" + renderEntries(entries) + ")";
        }
    }
}

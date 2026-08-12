package com.ss.influxdb.client;

import com.ss.common.toolbox.function.SerializableFunction;
import com.ss.influxdb.config.InfluxdbProperties;
import com.ss.influxdb.domain.InfluxPage;
import com.ss.influxdb.exception.InfluxOperationException;
import com.ss.influxdb.mapping.InfluxEntityMetadata;
import com.ss.influxdb.mapping.InfluxMetadataRegistry;
import com.ss.influxdb.mapping.InfluxPointMapper;
import com.ss.influxdb.mapping.InfluxResultMapper;
import com.ss.influxdb.query.InfluxIdentifiers;
import com.ss.influxdb.query.LambdaQueryWrapper;
import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * InfluxDB 1.x 同步写入、查询和数据库管理操作入口。
 *
 * <p>构造时会复制默认数据库、retention policy 和一致性配置，避免调用方后续修改配置对象
 * 影响正在使用的客户端操作。</p>
 */
public class InfluxOperations implements InfluxManagement {
    private final InfluxDB client;
    private final InfluxMetadataRegistry registry;
    private final InfluxPointMapper pointMapper;
    private final InfluxResultMapper resultMapper;
    /** 数据库管理操作委托。 */
    private final InfluxManagement management;
    private final String defaultDatabase;
    private final String defaultRetentionPolicy;
    private final InfluxDB.ConsistencyLevel consistency;

    /**
     * 创建操作入口。
     *
     * @param client       InfluxDB 客户端
     * @param properties   默认写入与查询配置
     * @param registry     实体元数据注册表
     * @param pointMapper  Point 映射器
     * @param resultMapper 查询结果映射器
     */
    public InfluxOperations(InfluxDB client, InfluxdbProperties properties,
                            InfluxMetadataRegistry registry, InfluxPointMapper pointMapper,
                            InfluxResultMapper resultMapper) {
        this.client = Objects.requireNonNull(client, "client");
        Objects.requireNonNull(properties, "properties");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.pointMapper = Objects.requireNonNull(pointMapper, "pointMapper");
        this.resultMapper = Objects.requireNonNull(resultMapper, "resultMapper");
        management = new InfluxManagementOperations(client);
        defaultDatabase = InfluxIdentifiers.identifier(properties.getDatabase().getName());
        String configuredRetentionPolicy = properties.getRetentionPolicy().getName();
        defaultRetentionPolicy = isBlank(configuredRetentionPolicy)
                ? null : InfluxIdentifiers.identifier(configuredRetentionPolicy);
        consistency = Objects.requireNonNull(properties.getConsistency(), "consistency");
    }

    /**
     * 将单个实体同步写入其注解或默认目标。
     *
     * @param entity 实体对象
     */
    public void save(Object entity) {
        Objects.requireNonNull(entity, "entity");
        InfluxEntityMetadata metadata = registry.metadata(entity.getClass());
        Destination destination = destination(metadata);
        Point point = pointMapper.toPoint(entity);
        try {
            if (client.isBatchEnabled()) {
                client.write(destination.database(), destination.retentionPolicy(), point);
            } else {
                writeBatch(destination, List.of(point));
            }
        } catch (RuntimeException exception) {
            throw new InfluxOperationException("Unable to write InfluxDB point", safeClientCause(exception));
        }
    }

    /**
     * 将实体集合按 database 和 retention policy 分组后同步批量写入。
     *
     * @param entities 实体集合
     */
    public void saveBatch(Collection<?> entities) {
        if (entities == null || entities.isEmpty()) {
            throw new IllegalArgumentException("InfluxDB batch entities must not be empty");
        }
        writeGroups(entities);
    }

    /**
     * 使用默认数据库执行调用方提供的完整 InfluxQL。
     *
     * @param influxql InfluxQL 查询语句
     * @return 返回的 {@code QueryResult} 结果
     */
    public QueryResult query(String influxql) {
        return query(influxql, defaultDatabase);
    }

    /**
     * 使用指定数据库执行调用方提供的完整 InfluxQL。
     *
     * @param influxql InfluxQL 查询语句
     * @param database 数据库名称
     * @return 返回的 {@code QueryResult} 结果
     */
    public QueryResult query(String influxql, String database) {
        if (influxql == null || influxql.isBlank()) {
            throw new IllegalArgumentException("InfluxQL query must not be blank");
        }
        String validatedDatabase = InfluxIdentifiers.identifier(database);
        try {
            QueryResult result = client.query(new Query(influxql, validatedDatabase));
            validateQueryResult(result);
            return result;
        } catch (InfluxOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InfluxOperationException("Unable to execute InfluxDB query", safeClientCause(exception));
        }
    }

    /**
     * 使用实体注解或默认数据库执行调用方提供的完整 InfluxQL。
     *
     * @param influxql InfluxQL 查询语句
     * @param entityType 实体类型
     * @return 返回的 {@code QueryResult} 结果
     */
    public QueryResult query(String influxql, Class<?> entityType) {
        Objects.requireNonNull(entityType, "entityType");
        return query(influxql, database(entityType));
    }

    /**
     * 执行安全查询构建器并映射全部实体。
     *
     * @param wrapper 查询包装器
     * @return 返回的 {@code List<T>} 结果
     */
    public <T> List<T> list(LambdaQueryWrapper<T> wrapper) {
        Objects.requireNonNull(wrapper, "wrapper");
        String database = wrapper.getDatabaseName() == null ? defaultDatabase : wrapper.getDatabaseName();
        return resultMapper.map(query(wrapper.build(), database), wrapper.getEntityType());
    }

    /**
     * 执行调用方提供的完整 InfluxQL，并按实体类型映射列表。
     *
     * @param influxql InfluxQL 查询语句
     * @param entityType 实体类型
     * @return 返回的 {@code List<T>} 结果
     */
    public <T> List<T> list(String influxql, Class<T> entityType) {
        Objects.requireNonNull(entityType, "entityType");
        return resultMapper.map(query(influxql, database(entityType)), entityType);
    }

    /**
     * 执行安全查询构建器并要求最多返回一条实体。
     *
     * @param wrapper 查询包装器
     * @return 返回的 {@code T} 结果
     */
    public <T> T one(LambdaQueryWrapper<T> wrapper) {
        List<T> records = list(wrapper);
        if (records.size() > 1) {
            throw new InfluxOperationException("InfluxDB query returned more than one record");
        }
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * 执行调用方提供的完整 InfluxQL，并要求最多映射一条实体。
     *
     * @param influxql InfluxQL 查询语句
     * @param entityType 实体类型
     * @return 返回的 {@code T} 结果
     */
    public <T> T one(String influxql, Class<T> entityType) {
        List<T> records = list(influxql, entityType);
        if (records.size() > 1) {
            throw new InfluxOperationException("InfluxDB query returned more than one record");
        }
        return records.isEmpty() ? null : records.get(0);
    }

    /**
     * 为实体类型创建安全查询构建器。
     *
     * @param entityType 实体类型
     * @return 返回的 {@code LambdaQueryWrapper<T>} 结果
     */
    public <T> LambdaQueryWrapper<T> wrapper(Class<T> entityType) {
        return LambdaQueryWrapper.of(entityType, registry);
    }

    /**
     * 执行分页查询，并使用调用方指定的非空字段统计总数。
     *
     * @param wrapper    基础查询条件
     * @param countField 业务上始终非空的计数字段
     * @param current    从 1 开始的当前页
     * @param pageSize   每页记录数

     *
     * @return 返回的 {@code InfluxPage<T>} 结果
     */
    public <T> InfluxPage<T> page(LambdaQueryWrapper<T> wrapper,
                                  SerializableFunction<T, ?> countField,
                                  int current, int pageSize) {
        Objects.requireNonNull(wrapper, "wrapper");
        Objects.requireNonNull(countField, "countField");
        if (current <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("InfluxDB page and page size must be greater than zero");
        }
        String database = wrapper.getDatabaseName() == null ? defaultDatabase : wrapper.getDatabaseName();
        long total = count(query(wrapper.buildCount(countField), database));
        if (total == 0L) {
            return new InfluxPage<>(0L, current, pageSize, List.of());
        }
        List<T> records = list(wrapper.copy().page(current, pageSize));
        return new InfluxPage<>(total, current, pageSize, records);
    }

    /** {@inheritDoc} */
    @Override
    public boolean databaseExists(String database) {
        return management.databaseExists(database);
    }

    /** {@inheritDoc} */
    @Override
    public void createDatabase(String database) {
        management.createDatabase(database);
    }

    /** {@inheritDoc} */
    @Override
    public boolean retentionPolicyExists(String database, String retentionPolicy) {
        return management.retentionPolicyExists(database, retentionPolicy);
    }

    /** {@inheritDoc} */
    @Override
    public void createRetentionPolicy(String database, String retentionPolicy, String duration,
                                      int replication, boolean defaultPolicy) {
        management.createRetentionPolicy(database, retentionPolicy, duration, replication, defaultPolicy);
    }

    private void writeGroups(Collection<?> entities) {
        Map<Destination, List<Point>> groups = new LinkedHashMap<>();
        for (Object entity : entities) {
            Objects.requireNonNull(entity, "InfluxDB entity");
            InfluxEntityMetadata metadata = registry.metadata(entity.getClass());
            Destination destination = destination(metadata);
            groups.computeIfAbsent(destination, ignored -> new ArrayList<>()).add(pointMapper.toPoint(entity));
        }
        try {
            for (Map.Entry<Destination, List<Point>> entry : groups.entrySet()) {
                writeBatch(entry.getKey(), entry.getValue());
            }
        } catch (RuntimeException exception) {
            throw new InfluxOperationException("Unable to write InfluxDB points", safeClientCause(exception));
        }
    }

    private Destination destination(InfluxEntityMetadata metadata) {
        return new Destination(
                metadata.getDatabaseName() == null ? defaultDatabase : metadata.getDatabaseName(),
                metadata.getRetentionPolicy() == null ? defaultRetentionPolicy : metadata.getRetentionPolicy());
    }

    private void writeBatch(Destination destination, List<Point> points) {
        BatchPoints.Builder builder = BatchPoints.database(destination.database())
                .consistency(consistency)
                .points(points);
        if (destination.retentionPolicy() != null) {
            builder.retentionPolicy(destination.retentionPolicy());
        }
        client.write(builder.build());
    }

    private String database(Class<?> entityType) {
        String database = registry.metadata(entityType).getDatabaseName();
        return database == null ? defaultDatabase : database;
    }

    private static void validateQueryResult(QueryResult result) {
        if (result == null) {
            throw new InfluxOperationException("InfluxDB query returned no result object");
        }
        if (result.hasError()) {
            throw new InfluxOperationException("InfluxDB query returned a server error");
        }
        if (result.getResults() != null) {
            for (QueryResult.Result item : result.getResults()) {
                if (item != null && item.hasError()) {
                    throw new InfluxOperationException("InfluxDB query result item returned a server error");
                }
            }
        }
    }

    private static long count(QueryResult result) {
        if (result.getResults() == null) {
            return 0L;
        }
        long total = 0L;
        for (QueryResult.Result item : result.getResults()) {
            if (item == null || item.getSeries() == null) {
                continue;
            }
            for (QueryResult.Series series : item.getSeries()) {
                int countIndex = series == null || series.getColumns() == null
                        ? -1 : series.getColumns().indexOf("ss_total");
                if (countIndex < 0 || series.getValues() == null) {
                    continue;
                }
                for (List<Object> row : series.getValues()) {
                    if (row == null || countIndex >= row.size() || row.get(countIndex) == null) {
                        continue;
                    }
                    Object value = row.get(countIndex);
                    if (!(value instanceof Number number)) {
                        throw new InfluxOperationException("InfluxDB count result is not numeric");
                    }
                    double decimal = number.doubleValue();
                    long count = number.longValue();
                    if (!Double.isFinite(decimal) || decimal < 0 || decimal != count) {
                        throw new InfluxOperationException("InfluxDB count result is invalid");
                    }
                    try {
                        total = Math.addExact(total, count);
                    } catch (ArithmeticException exception) {
                        throw new InfluxOperationException("InfluxDB count result is too large", exception);
                    }
                }
            }
        }
        return total;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static RuntimeException safeClientCause(RuntimeException exception) {
        return new IllegalStateException(
                "InfluxDB client failure type: " + exception.getClass().getName());
    }

    private record Destination(String database, String retentionPolicy) {
    }
}

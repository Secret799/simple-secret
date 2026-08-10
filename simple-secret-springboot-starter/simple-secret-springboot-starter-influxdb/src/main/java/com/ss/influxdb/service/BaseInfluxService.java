package com.ss.influxdb.service;

import com.ss.common.toolbox.function.SerializableFunction;
import com.ss.influxdb.client.InfluxOperations;
import com.ss.influxdb.domain.InfluxPage;
import com.ss.influxdb.query.LambdaQueryWrapper;
import org.influxdb.dto.QueryResult;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 使用显式实体类型和操作入口实现通用 InfluxDB 服务。
 *
 * @param <T> 实体类型
 */
public abstract class BaseInfluxService<T> implements InfluxService<T> {
    private final InfluxOperations operations;
    private final Class<T> entityType;

    /**
     * 创建服务，不通过运行时代理猜测泛型。
     *
     * @param operations InfluxDB 操作入口
     * @param entityType 实体类型
     */
    protected BaseInfluxService(InfluxOperations operations, Class<T> entityType) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    @Override
    public final Class<T> getEntityType() {
        return entityType;
    }

    @Override
    public void save(T entity) {
        operations.save(entity);
    }

    @Override
    public void saveBatch(Collection<? extends T> entities) {
        operations.saveBatch(entities);
    }

    @Override
    public List<T> list() {
        return operations.list(wrapper());
    }

    @Override
    public List<T> list(LambdaQueryWrapper<T> wrapper) {
        return operations.list(wrapper);
    }

    @Override
    public List<T> list(String influxql) {
        return operations.list(influxql, entityType);
    }

    @Override
    public T one(LambdaQueryWrapper<T> wrapper) {
        return operations.one(wrapper);
    }

    @Override
    public T one(String influxql) {
        return operations.one(influxql, entityType);
    }

    @Override
    public InfluxPage<T> page(LambdaQueryWrapper<T> wrapper, SerializableFunction<T, ?> countField,
                              int current, int pageSize) {
        return operations.page(wrapper, countField, current, pageSize);
    }

    @Override
    public QueryResult query(String influxql) {
        return operations.query(influxql, entityType);
    }

    @Override
    public LambdaQueryWrapper<T> wrapper() {
        return operations.wrapper(entityType);
    }
}

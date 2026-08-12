package com.ss.influxdb.service;

import com.ss.common.toolbox.function.SerializableFunction;
import com.ss.influxdb.domain.InfluxPage;
import com.ss.influxdb.query.LambdaQueryWrapper;
import org.influxdb.dto.QueryResult;

import java.util.Collection;
import java.util.List;

/**
 * 面向单一 InfluxDB 实体类型的通用服务契约。
 *
 * @param <T> 实体类型
 */
public interface InfluxService<T> {
    /** @return 服务负责的实体类型 */
    Class<T> getEntityType();

    /**
     * 写入单个实体。
     *
     * @param entity 实体对象
     */
    void save(T entity);

    /**
     * 批量写入实体。
     *
     * @param entities 实体集合
     */
    void saveBatch(Collection<? extends T> entities);

    /**
     * 查询全部实体。
     *
     * @return 返回的 {@code List<T>} 结果
     */
    List<T> list();

    /**
     * 执行安全查询构建器并返回列表。
     *
     * @param wrapper 查询包装器
     * @return 返回的 {@code List<T>} 结果
     */
    List<T> list(LambdaQueryWrapper<T> wrapper);

    /**
     * 执行可信完整 InfluxQL 并返回列表。
     *
     * @param influxql InfluxQL 查询语句
     * @return 返回的 {@code List<T>} 结果
     */
    List<T> list(String influxql);

    /**
     * 执行安全查询构建器并要求最多一条记录。
     *
     * @param wrapper 查询包装器
     * @return 返回的 {@code T} 结果
     */
    T one(LambdaQueryWrapper<T> wrapper);

    /**
     * 执行可信完整 InfluxQL 并要求最多一条记录。
     *
     * @param influxql InfluxQL 查询语句
     * @return 返回的 {@code T} 结果
     */
    T one(String influxql);

    /**
     * 执行分页查询。
     *
     * @param wrapper 查询包装器
     * @param countField 用于统计的实体字段
     * @param current 当前值
     * @param pageSize 分页大小
     * @return 返回的 {@code InfluxPage<T>} 结果
     */
    InfluxPage<T> page(LambdaQueryWrapper<T> wrapper, SerializableFunction<T, ?> countField,
                       int current, int pageSize);

    /**
     * 执行可信完整 InfluxQL 并返回原始结果。
     *
     * @param influxql InfluxQL 查询语句
     * @return 返回的 {@code QueryResult} 结果
     */
    QueryResult query(String influxql);

    /**
     * 创建实体类型对应的安全查询构建器。
     *
     * @return 返回的 {@code LambdaQueryWrapper<T>} 结果
     */
    LambdaQueryWrapper<T> wrapper();
}

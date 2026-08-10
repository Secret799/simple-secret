package com.ss.influxdb.domain;

import com.ss.influxdb.mapping.InfluxEntityMetadata;
import com.ss.influxdb.mapping.InfluxMetadataRegistry;

/**
 * 为注解实体提供 measurement、database 和 retention policy 的便捷读取方法。
 *
 * <p>实体映射不要求继承该类型，普通 InfluxDB 注解 POJO 同样受支持。</p>
 */
public abstract class BaseInfluxModel {
    private static final InfluxMetadataRegistry METADATA_REGISTRY = new InfluxMetadataRegistry();

    /** @return measurement 名称 */
    public final String getMeasurementName() {
        return metadata().getMeasurementName();
    }

    /** @return 实体注解数据库名；未指定时为 {@code null} */
    public final String getDatabaseName() {
        return metadata().getDatabaseName();
    }

    /** @return 实体注解 retention policy；未指定时为 {@code null} */
    public final String getRetentionPolicy() {
        return metadata().getRetentionPolicy();
    }

    private InfluxEntityMetadata metadata() {
        return METADATA_REGISTRY.metadata(getClass());
    }
}

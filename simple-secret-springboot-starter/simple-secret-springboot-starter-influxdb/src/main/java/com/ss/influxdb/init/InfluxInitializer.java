package com.ss.influxdb.init;

import com.ss.influxdb.client.InfluxManagement;
import com.ss.influxdb.client.InfluxOperations;
import com.ss.influxdb.config.InfluxdbProperties;
import com.ss.influxdb.exception.InfluxOperationException;
import org.springframework.beans.factory.InitializingBean;

import java.util.Objects;

/**
 * 在 Spring 容器初始化阶段执行显式启用的数据库和 retention policy 创建。
 */
public class InfluxInitializer implements InitializingBean {
    private final InfluxManagement management;
    private final String database;
    private final boolean createDatabase;
    private final String retentionPolicy;
    private final String retentionDuration;
    private final boolean createRetentionPolicy;
    private final int replication;
    private final boolean defaultPolicy;

    /** 创建初始化器并复制初始化配置。 */
    public InfluxInitializer(InfluxOperations operations, InfluxdbProperties properties) {
        this((InfluxManagement) operations, properties);
    }

    /**
     * 创建初始化器并复制初始化配置。
     *
     * @param management 数据库管理入口
     * @param properties 初始化配置
     */
    public InfluxInitializer(InfluxManagement management, InfluxdbProperties properties) {
        this.management = Objects.requireNonNull(management, "management");
        Objects.requireNonNull(properties, "properties");
        database = properties.getDatabase().getName();
        createDatabase = properties.getDatabase().isAutoCreate();
        retentionPolicy = properties.getRetentionPolicy().getName();
        retentionDuration = properties.getRetentionPolicy().getDuration();
        createRetentionPolicy = properties.getRetentionPolicy().isAutoCreate();
        replication = properties.getRetentionPolicy().getReplication();
        defaultPolicy = properties.getRetentionPolicy().isDefaultPolicy();
    }

    /** 先准备数据库，再准备 retention policy。 */
    @Override
    public void afterPropertiesSet() {
        if (!createDatabase && !createRetentionPolicy) {
            return;
        }
        boolean databaseExists = management.databaseExists(database);
        if (!databaseExists && createDatabase) {
            management.createDatabase(database);
            databaseExists = true;
        }
        if (createRetentionPolicy) {
            if (!databaseExists) {
                throw new InfluxOperationException(
                        "InfluxDB database must exist before creating retention policy");
            }
            if (!management.retentionPolicyExists(database, retentionPolicy)) {
                management.createRetentionPolicy(database, retentionPolicy, retentionDuration,
                        replication, defaultPolicy);
            }
        }
    }
}

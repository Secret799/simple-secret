package com.ss.influxdb.client;

/**
 * InfluxDB 数据库和 retention policy 管理能力。
 *
 * @author junpzx
 * @since 2026-08-10
 */
public interface InfluxManagement {
    /**
     * 判断数据库是否存在。
     *
     * @param database 数据库名
     * @return 存在时返回 {@code true}
     */
    boolean databaseExists(String database);

    /**
     * 创建数据库。
     *
     * @param database 数据库名
     */
    void createDatabase(String database);

    /**
     * 判断 retention policy 是否存在。
     *
     * @param database        数据库名
     * @param retentionPolicy retention policy 名称
     * @return 存在时返回 {@code true}
     */
    boolean retentionPolicyExists(String database, String retentionPolicy);

    /**
     * 创建 retention policy。
     *
     * @param database        数据库名
     * @param retentionPolicy retention policy 名称
     * @param duration        InfluxDB duration
     * @param replication     副本数
     * @param defaultPolicy   是否设置为默认策略
     */
    void createRetentionPolicy(String database, String retentionPolicy, String duration,
                               int replication, boolean defaultPolicy);
}

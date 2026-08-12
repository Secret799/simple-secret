package com.ss.influxdb.config;

import org.influxdb.InfluxDB;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * {@code simple-secret.influxdb} 下的 InfluxDB 1.x 客户端配置。
 */
@ConfigurationProperties("simple-secret.influxdb")
public class InfluxdbProperties {
    /**
     * 是否启用。
     */
    private boolean enabled;
    /**
     * 服务连接地址。
     */
    private String url;
    /**
     * 用户名。
     */
    private String username;
    /**
     * 密码。
     */
    private String password;
    /**
     * {@code connect}超时时间，单位毫秒。
     */
    private long connectTimeoutMillis = 10_000L;
    /**
     * {@code read}超时时间，单位毫秒。
     */
    private long readTimeoutMillis = 10_000L;
    /**
     * {@code write}超时时间，单位毫秒。
     */
    private long writeTimeoutMillis = 10_000L;
    /**
     * InfluxDB 写一致性级别。
     */
    private InfluxDB.ConsistencyLevel consistency = InfluxDB.ConsistencyLevel.ONE;
    /**
     * InfluxDB 客户端日志级别。
     */
    private InfluxDB.LogLevel logLevel = InfluxDB.LogLevel.NONE;
    /**
     * 数据库名称。
     */
    private DatabaseConfig database = new DatabaseConfig();
    /**
     * InfluxDB 保留策略名称。
     */
    private RetentionPolicyConfig retentionPolicy = new RetentionPolicyConfig();
    /**
     * InfluxDB 批量写入配置。
     */
    private BatchWriteConfig batchWrite = new BatchWriteConfig();

    /** @return 是否启用客户端 */ public boolean isEnabled() { return enabled; }
    /** @param value 是否启用客户端 */ public void setEnabled(boolean value) { enabled = value; }
    /** @return InfluxDB HTTP(S) 地址 */ public String getUrl() { return url; }
    /** @param value InfluxDB HTTP(S) 地址 */ public void setUrl(String value) { url = value; }
    /** @return 用户名 */ public String getUsername() { return username; }
    /** @param value 用户名 */ public void setUsername(String value) { username = value; }
    /** @return 密码 */ public String getPassword() { return password; }
    /** @param value 密码 */ public void setPassword(String value) { password = value; }
    /** @return 连接超时毫秒数 */ public long getConnectTimeoutMillis() { return connectTimeoutMillis; }
    /** @param value 连接超时毫秒数 */
    public void setConnectTimeoutMillis(long value) { connectTimeoutMillis = value; }
    /** @return 读取超时毫秒数 */ public long getReadTimeoutMillis() { return readTimeoutMillis; }
    /** @param value 读取超时毫秒数 */
    public void setReadTimeoutMillis(long value) { readTimeoutMillis = value; }
    /** @return 写入超时毫秒数 */ public long getWriteTimeoutMillis() { return writeTimeoutMillis; }
    /** @param value 写入超时毫秒数 */
    public void setWriteTimeoutMillis(long value) { writeTimeoutMillis = value; }
    /** @return 默认写一致性 */ public InfluxDB.ConsistencyLevel getConsistency() { return consistency; }
    /** @param value 默认写一致性 */
    public void setConsistency(InfluxDB.ConsistencyLevel value) { consistency = value; }
    /** @return HTTP 日志级别 */ public InfluxDB.LogLevel getLogLevel() { return logLevel; }
    /** @param value HTTP 日志级别 */ public void setLogLevel(InfluxDB.LogLevel value) { logLevel = value; }
    /** @return 数据库配置 */ public DatabaseConfig getDatabase() { return database; }
    /** @param value 数据库配置 */ public void setDatabase(DatabaseConfig value) {
        database = value == null ? new DatabaseConfig() : value;
    }
    /** @return retention policy 配置 */ public RetentionPolicyConfig getRetentionPolicy() { return retentionPolicy; }
    /** @param value retention policy 配置 */ public void setRetentionPolicy(RetentionPolicyConfig value) {
        retentionPolicy = value == null ? new RetentionPolicyConfig() : value;
    }
    /** @return batch 写配置 */ public BatchWriteConfig getBatchWrite() { return batchWrite; }
    /** @param value batch 写配置 */ public void setBatchWrite(BatchWriteConfig value) {
        batchWrite = value == null ? new BatchWriteConfig() : value;
    }

    /** 校验显式启用后的连接和初始化配置。 */
    public void validate() {
        if (!enabled) {
            return;
        }
        validateUrl();
        if (isBlank(database.name)) {
            throw new IllegalArgumentException("InfluxDB database name is required");
        }
        if (!isBlank(password) && isBlank(username)) {
            throw new IllegalArgumentException("InfluxDB username is required when password is configured");
        }
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0 || writeTimeoutMillis <= 0) {
            throw new IllegalArgumentException("InfluxDB HTTP timeouts must be positive");
        }
        if (consistency == null || logLevel == null) {
            throw new IllegalArgumentException("InfluxDB consistency and log level are required");
        }
        if (batchWrite.enabled && batchWrite.actions <= 0) {
            throw new IllegalArgumentException("InfluxDB batch actions must be positive");
        }
        if (batchWrite.enabled && batchWrite.flushDurationMillis <= 0) {
            throw new IllegalArgumentException("InfluxDB batch flush duration must be positive");
        }
        if (batchWrite.enabled && batchWrite.consistency == null) {
            throw new IllegalArgumentException("InfluxDB batch consistency is required");
        }
        if (retentionPolicy.autoCreate && isBlank(retentionPolicy.name)) {
            throw new IllegalArgumentException("InfluxDB retention policy name is required for auto-create");
        }
        if (retentionPolicy.autoCreate && isBlank(retentionPolicy.duration)) {
            throw new IllegalArgumentException("InfluxDB retention policy duration is required for auto-create");
        }
        if (retentionPolicy.autoCreate && retentionPolicy.replication <= 0) {
            throw new IllegalArgumentException("InfluxDB retention policy replication must be positive");
        }
    }

    private void validateUrl() {
        if (isBlank(url)) {
            throw new IllegalArgumentException("InfluxDB URL is required");
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!("http".equals(scheme.toLowerCase(Locale.ROOT)))
                    && !("https".equals(scheme.toLowerCase(Locale.ROOT))))) {
                throw new IllegalArgumentException("InfluxDB URL must use HTTP or HTTPS");
            }
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("InfluxDB URL requires a host");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalArgumentException(
                        "InfluxDB URL userinfo is forbidden; use username and password properties");
            }
            if (uri.getFragment() != null) {
                throw new IllegalArgumentException("InfluxDB URL fragment is forbidden");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("InfluxDB URL is invalid", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 默认数据库及显式自动创建配置。 */
    public static class DatabaseConfig {
        /**
         * 名称。
         */
        private String name;
        /**
         * 是否自动创建资源。
         */
        private boolean autoCreate;

        /** @return 数据库名称 */ public String getName() { return name; }
        /** @param value 数据库名称 */ public void setName(String value) { name = value; }
        /** @return 是否自动创建 */ public boolean isAutoCreate() { return autoCreate; }
        /** @param value 是否自动创建 */ public void setAutoCreate(boolean value) { autoCreate = value; }
    }

    /** Retention policy 与显式自动创建配置。 */
    public static class RetentionPolicyConfig {
        /**
         * 名称。
         */
        private String name;
        /**
         * 持续时间。
         */
        private String duration;
        /**
         * 是否自动创建资源。
         */
        private boolean autoCreate;
        /**
         * InfluxDB 保留策略副本数。
         */
        private int replication = 1;
        /**
         * 是否为默认保留策略。
         */
        private boolean defaultPolicy;

        /** @return 策略名称 */ public String getName() { return name; }
        /** @param value 策略名称 */ public void setName(String value) { name = value; }
        /** @return InfluxQL duration */ public String getDuration() { return duration; }
        /** @param value InfluxQL duration */ public void setDuration(String value) { duration = value; }
        /** @return 是否自动创建 */ public boolean isAutoCreate() { return autoCreate; }
        /** @param value 是否自动创建 */ public void setAutoCreate(boolean value) { autoCreate = value; }
        /** @return 副本数 */ public int getReplication() { return replication; }
        /** @param value 副本数 */ public void setReplication(int value) { replication = value; }
        /** @return 是否设为默认策略 */ public boolean isDefaultPolicy() { return defaultPolicy; }
        /** @param value 是否设为默认策略 */
        public void setDefaultPolicy(boolean value) { defaultPolicy = value; }
    }

    /** InfluxDB Java 客户端 batch 写入配置。 */
    public static class BatchWriteConfig {
        /**
         * 是否启用。
         */
        private boolean enabled;
        /**
         * InfluxDB 写一致性级别。
         */
        private InfluxDB.ConsistencyLevel consistency = InfluxDB.ConsistencyLevel.ONE;
        /**
         * 航点动作列表。
         */
        private int actions = 1_000;
        /**
         * {@code flushDuration}，单位毫秒。
         */
        private int flushDurationMillis = 1_000;

        /** @return 是否启用 batch */ public boolean isEnabled() { return enabled; }
        /** @param value 是否启用 batch */ public void setEnabled(boolean value) { enabled = value; }
        /** @return batch 一致性 */ public InfluxDB.ConsistencyLevel getConsistency() { return consistency; }
        /** @param value batch 一致性 */
        public void setConsistency(InfluxDB.ConsistencyLevel value) { consistency = value; }
        /** @return 触发写入的 point 数 */ public int getActions() { return actions; }
        /** @param value 触发写入的 point 数 */ public void setActions(int value) { actions = value; }
        /** @return flush 间隔毫秒数 */ public int getFlushDurationMillis() { return flushDurationMillis; }
        /** @param value flush 间隔毫秒数 */
        public void setFlushDurationMillis(int value) { flushDurationMillis = value; }
    }
}

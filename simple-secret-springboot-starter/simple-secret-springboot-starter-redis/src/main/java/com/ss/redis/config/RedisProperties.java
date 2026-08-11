package com.ss.redis.config;

import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple Secret Redis 配置。
 *
 * <p>默认不创建 Redis 连接。只有显式设置 {@code enabled=true} 时，starter
 * 才使用这里的连接参数创建客户端。</p>
 */
@ConfigurationProperties(prefix = "simple-secret.redis")
public class RedisProperties {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofSeconds(10);

    /** 是否允许 starter 创建 Redisson 客户端。 */
    private boolean enabled;

    /** Redis 连接模式。 */
    private RedisMode mode = RedisMode.SINGLE;

    /** 应用于 Redisson 对象名称的统一前缀。 */
    private String keyPrefix = "";

    /** Redisson 工作线程数。 */
    private int threads = 16;

    /** Redisson Netty 线程数。 */
    private int nettyThreads = 32;

    /** 是否缓存 Lua 脚本。 */
    private boolean useScriptCache = true;

    /** 单机连接参数。 */
    private Single single = new Single();

    /** 集群连接参数。 */
    private Cluster cluster = new Cluster();

    /** Spring Cache 可选配置。 */
    private Cache cache = new Cache();

    /**
     * 校验创建 Redisson 客户端所需的配置。
     *
     * @throws IllegalStateException 配置不完整或超出 Redisson 支持范围时抛出
     */
    public void validate() {
        if (cache != null && cache.isEnabled()) {
            cache.validate();
        }
        if (!enabled) {
            return;
        }
        if (mode == null) {
            throw invalid("mode", "must be SINGLE or CLUSTER");
        }
        requirePositive("threads", threads);
        requirePositive("netty-threads", nettyThreads);
        if (mode == RedisMode.SINGLE) {
            requireNonNull("single", single);
            single.validate();
        } else {
            requireNonNull("cluster", cluster);
            cluster.validate();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RedisMode getMode() {
        return mode;
    }

    public void setMode(RedisMode mode) {
        this.mode = mode;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix.trim();
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getNettyThreads() {
        return nettyThreads;
    }

    public void setNettyThreads(int nettyThreads) {
        this.nettyThreads = nettyThreads;
    }

    public boolean isUseScriptCache() {
        return useScriptCache;
    }

    public void setUseScriptCache(boolean useScriptCache) {
        this.useScriptCache = useScriptCache;
    }

    public Single getSingle() {
        return single;
    }

    public void setSingle(Single single) {
        this.single = single;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    @Override
    public String toString() {
        return "RedisProperties{" +
                "enabled=" + enabled +
                ", mode=" + mode +
                ", keyPrefix='" + keyPrefix + '\'' +
                ", threads=" + threads +
                ", nettyThreads=" + nettyThreads +
                ", useScriptCache=" + useScriptCache +
                ", single=" + single +
                ", cluster=" + cluster +
                ", cache=" + cache +
                '}';
    }

    /** 单机 Redis 连接参数。 */
    public static class Single {

        private String address;
        private String username;
        private String password;
        private String clientName;
        private int database;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration timeout = DEFAULT_COMMAND_TIMEOUT;
        private Duration idleConnectionTimeout = DEFAULT_IDLE_TIMEOUT;
        private int connectionMinimumIdleSize = 24;
        private int connectionPoolSize = 64;
        private int subscriptionConnectionMinimumIdleSize = 1;
        private int subscriptionConnectionPoolSize = 50;

        private void validate() {
            requireRedisAddress("single.address", address);
            requireNonNegative("single.database", database);
            requirePositiveDuration("single.connect-timeout", connectTimeout);
            requirePositiveDuration("single.timeout", timeout);
            requirePositiveDuration("single.idle-connection-timeout", idleConnectionTimeout);
            requirePool("single.connection", connectionMinimumIdleSize, connectionPoolSize);
            requirePool("single.subscription-connection",
                    subscriptionConnectionMinimumIdleSize, subscriptionConnectionPoolSize);
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = trimToNull(address);
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = trimToNull(username);
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = trimToNull(clientName);
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Duration getIdleConnectionTimeout() {
            return idleConnectionTimeout;
        }

        public void setIdleConnectionTimeout(Duration idleConnectionTimeout) {
            this.idleConnectionTimeout = idleConnectionTimeout;
        }

        public int getConnectionMinimumIdleSize() {
            return connectionMinimumIdleSize;
        }

        public void setConnectionMinimumIdleSize(int connectionMinimumIdleSize) {
            this.connectionMinimumIdleSize = connectionMinimumIdleSize;
        }

        public int getConnectionPoolSize() {
            return connectionPoolSize;
        }

        public void setConnectionPoolSize(int connectionPoolSize) {
            this.connectionPoolSize = connectionPoolSize;
        }

        public int getSubscriptionConnectionMinimumIdleSize() {
            return subscriptionConnectionMinimumIdleSize;
        }

        public void setSubscriptionConnectionMinimumIdleSize(int subscriptionConnectionMinimumIdleSize) {
            this.subscriptionConnectionMinimumIdleSize = subscriptionConnectionMinimumIdleSize;
        }

        public int getSubscriptionConnectionPoolSize() {
            return subscriptionConnectionPoolSize;
        }

        public void setSubscriptionConnectionPoolSize(int subscriptionConnectionPoolSize) {
            this.subscriptionConnectionPoolSize = subscriptionConnectionPoolSize;
        }

        @Override
        public String toString() {
            return "Single{" +
                    "address='" + address + '\'' +
                    ", clientName='" + clientName + '\'' +
                    ", database=" + database +
                    ", connectTimeout=" + connectTimeout +
                    ", timeout=" + timeout +
                    ", idleConnectionTimeout=" + idleConnectionTimeout +
                    ", connectionMinimumIdleSize=" + connectionMinimumIdleSize +
                    ", connectionPoolSize=" + connectionPoolSize +
                    ", subscriptionConnectionMinimumIdleSize=" + subscriptionConnectionMinimumIdleSize +
                    ", subscriptionConnectionPoolSize=" + subscriptionConnectionPoolSize +
                    '}';
        }
    }

    /** Redis Cluster 连接参数。 */
    public static class Cluster {

        private List<String> nodeAddresses = List.of();
        private String username;
        private String password;
        private String clientName;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration timeout = DEFAULT_COMMAND_TIMEOUT;
        private Duration idleConnectionTimeout = DEFAULT_IDLE_TIMEOUT;
        private Duration scanInterval = Duration.ofSeconds(5);
        private int masterConnectionMinimumIdleSize = 24;
        private int masterConnectionPoolSize = 64;
        private int slaveConnectionMinimumIdleSize = 24;
        private int slaveConnectionPoolSize = 64;
        private int subscriptionConnectionMinimumIdleSize = 1;
        private int subscriptionConnectionPoolSize = 50;
        private ReadMode readMode = ReadMode.SLAVE;
        private SubscriptionMode subscriptionMode = SubscriptionMode.MASTER;

        private void validate() {
            if (nodeAddresses == null || nodeAddresses.isEmpty()) {
                throw invalid("cluster.node-addresses", "must contain at least one Redis address");
            }
            for (int index = 0; index < nodeAddresses.size(); index++) {
                requireRedisAddress("cluster.node-addresses[" + index + "]", nodeAddresses.get(index));
            }
            requirePositiveDuration("cluster.connect-timeout", connectTimeout);
            requirePositiveDuration("cluster.timeout", timeout);
            requirePositiveDuration("cluster.idle-connection-timeout", idleConnectionTimeout);
            requirePositiveDuration("cluster.scan-interval", scanInterval);
            requirePool("cluster.master-connection",
                    masterConnectionMinimumIdleSize, masterConnectionPoolSize);
            requirePool("cluster.slave-connection",
                    slaveConnectionMinimumIdleSize, slaveConnectionPoolSize);
            requirePool("cluster.subscription-connection",
                    subscriptionConnectionMinimumIdleSize, subscriptionConnectionPoolSize);
            requireNonNull("cluster.read-mode", readMode);
            requireNonNull("cluster.subscription-mode", subscriptionMode);
        }

        public List<String> getNodeAddresses() {
            return nodeAddresses;
        }

        public void setNodeAddresses(List<String> nodeAddresses) {
            this.nodeAddresses = nodeAddresses == null ? List.of() : List.copyOf(nodeAddresses);
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = trimToNull(username);
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = trimToNull(clientName);
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Duration getIdleConnectionTimeout() {
            return idleConnectionTimeout;
        }

        public void setIdleConnectionTimeout(Duration idleConnectionTimeout) {
            this.idleConnectionTimeout = idleConnectionTimeout;
        }

        public Duration getScanInterval() {
            return scanInterval;
        }

        public void setScanInterval(Duration scanInterval) {
            this.scanInterval = scanInterval;
        }

        public int getMasterConnectionMinimumIdleSize() {
            return masterConnectionMinimumIdleSize;
        }

        public void setMasterConnectionMinimumIdleSize(int masterConnectionMinimumIdleSize) {
            this.masterConnectionMinimumIdleSize = masterConnectionMinimumIdleSize;
        }

        public int getMasterConnectionPoolSize() {
            return masterConnectionPoolSize;
        }

        public void setMasterConnectionPoolSize(int masterConnectionPoolSize) {
            this.masterConnectionPoolSize = masterConnectionPoolSize;
        }

        public int getSlaveConnectionMinimumIdleSize() {
            return slaveConnectionMinimumIdleSize;
        }

        public void setSlaveConnectionMinimumIdleSize(int slaveConnectionMinimumIdleSize) {
            this.slaveConnectionMinimumIdleSize = slaveConnectionMinimumIdleSize;
        }

        public int getSlaveConnectionPoolSize() {
            return slaveConnectionPoolSize;
        }

        public void setSlaveConnectionPoolSize(int slaveConnectionPoolSize) {
            this.slaveConnectionPoolSize = slaveConnectionPoolSize;
        }

        public int getSubscriptionConnectionMinimumIdleSize() {
            return subscriptionConnectionMinimumIdleSize;
        }

        public void setSubscriptionConnectionMinimumIdleSize(int subscriptionConnectionMinimumIdleSize) {
            this.subscriptionConnectionMinimumIdleSize = subscriptionConnectionMinimumIdleSize;
        }

        public int getSubscriptionConnectionPoolSize() {
            return subscriptionConnectionPoolSize;
        }

        public void setSubscriptionConnectionPoolSize(int subscriptionConnectionPoolSize) {
            this.subscriptionConnectionPoolSize = subscriptionConnectionPoolSize;
        }

        public ReadMode getReadMode() {
            return readMode;
        }

        public void setReadMode(ReadMode readMode) {
            this.readMode = readMode;
        }

        public SubscriptionMode getSubscriptionMode() {
            return subscriptionMode;
        }

        public void setSubscriptionMode(SubscriptionMode subscriptionMode) {
            this.subscriptionMode = subscriptionMode;
        }

        @Override
        public String toString() {
            return "Cluster{" +
                    "nodeAddresses=" + nodeAddresses +
                    ", clientName='" + clientName + '\'' +
                    ", connectTimeout=" + connectTimeout +
                    ", timeout=" + timeout +
                    ", idleConnectionTimeout=" + idleConnectionTimeout +
                    ", scanInterval=" + scanInterval +
                    ", masterConnectionMinimumIdleSize=" + masterConnectionMinimumIdleSize +
                    ", masterConnectionPoolSize=" + masterConnectionPoolSize +
                    ", slaveConnectionMinimumIdleSize=" + slaveConnectionMinimumIdleSize +
                    ", slaveConnectionPoolSize=" + slaveConnectionPoolSize +
                    ", subscriptionConnectionMinimumIdleSize=" + subscriptionConnectionMinimumIdleSize +
                    ", subscriptionConnectionPoolSize=" + subscriptionConnectionPoolSize +
                    ", readMode=" + readMode +
                    ", subscriptionMode=" + subscriptionMode +
                    '}';
        }
    }

    /** Spring Cache 总开关和缓存定义。 */
    public static class Cache {

        private boolean enabled;
        private boolean allowNullValues;
        private boolean transactionAware;
        private Map<String, CacheSpec> caches = Map.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAllowNullValues() {
            return allowNullValues;
        }

        public void setAllowNullValues(boolean allowNullValues) {
            this.allowNullValues = allowNullValues;
        }

        public boolean isTransactionAware() {
            return transactionAware;
        }

        public void setTransactionAware(boolean transactionAware) {
            this.transactionAware = transactionAware;
        }

        public Map<String, CacheSpec> getCaches() {
            return caches;
        }

        public void setCaches(Map<String, CacheSpec> caches) {
            this.caches = caches == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(caches));
        }

        /** 校验所有显式缓存定义。 */
        public void validate() {
            if (!enabled) {
                return;
            }
            for (Map.Entry<String, CacheSpec> entry : caches.entrySet()) {
                String name = entry.getKey();
                if (name == null || name.isBlank()) {
                    throw invalid("cache.caches", "must not contain a blank cache name");
                }
                CacheSpec spec = entry.getValue();
                if (spec == null) {
                    throw invalid("cache.caches." + name, "must not be null");
                }
                spec.validate(name);
            }
        }

        @Override
        public String toString() {
            return "Cache{" +
                    "enabled=" + enabled +
                    ", allowNullValues=" + allowNullValues +
                    ", transactionAware=" + transactionAware +
                    ", caches=" + caches +
                    '}';
        }
    }

    /** 单个 Spring Cache 的过期和容量配置。 */
    public static class CacheSpec {

        private Duration ttl = Duration.ZERO;
        private Duration maxIdleTime = Duration.ZERO;
        private int maxSize;

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getMaxIdleTime() {
            return maxIdleTime;
        }

        public void setMaxIdleTime(Duration maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        private void validate(String cacheName) {
            requireNonNegativeDuration("cache.caches." + cacheName + ".ttl", ttl);
            requireNonNegativeDuration(
                    "cache.caches." + cacheName + ".max-idle-time", maxIdleTime);
            if (maxSize < 0) {
                throw invalid("cache.caches." + cacheName + ".max-size", "must not be negative");
            }
        }

        @Override
        public String toString() {
            return "CacheSpec{" +
                    "ttl=" + ttl +
                    ", maxIdleTime=" + maxIdleTime +
                    ", maxSize=" + maxSize +
                    '}';
        }
    }

    private static void requireRedisAddress(String property, String address) {
        if (address == null || address.isBlank()) {
            throw invalid(property, "must not be blank");
        }
        try {
            URI uri = new URI(address);
            String scheme = uri.getScheme();
            if (!("redis".equalsIgnoreCase(scheme) || "rediss".equalsIgnoreCase(scheme))) {
                throw invalid(property, "must use redis:// or rediss://");
            }
            if (uri.getHost() == null || uri.getPort() < 1 || uri.getUserInfo() != null) {
                throw invalid(property, "must contain a host and port without embedded credentials");
            }
        } catch (URISyntaxException exception) {
            throw invalid(property, "must be a valid redis:// or rediss:// URI");
        }
    }

    private static void requirePositiveDuration(String property, Duration value) {
        requireNonNull(property, value);
        long millis;
        try {
            millis = value.toMillis();
        } catch (ArithmeticException exception) {
            throw invalid(property, "is too large");
        }
        if (value.isZero() || value.isNegative() || millis > Integer.MAX_VALUE) {
            throw invalid(property, "must be positive and no greater than " + Integer.MAX_VALUE + "ms");
        }
    }

    private static void requireNonNegativeDuration(String property, Duration value) {
        requireNonNull(property, value);
        try {
            value.toMillis();
        } catch (ArithmeticException exception) {
            throw invalid(property, "is too large");
        }
        if (value.isNegative()) {
            throw invalid(property, "must not be negative");
        }
    }

    private static void requirePool(String propertyPrefix, int minimumIdleSize, int poolSize) {
        requirePositive(propertyPrefix + "-minimum-idle-size", minimumIdleSize);
        requirePositive(propertyPrefix + "-pool-size", poolSize);
        if (minimumIdleSize > poolSize) {
            throw invalid(propertyPrefix + "-pool-size", "must be greater than or equal to minimum idle size");
        }
    }

    private static void requirePositive(String property, int value) {
        if (value <= 0) {
            throw invalid(property, "must be positive");
        }
    }

    private static void requireNonNegative(String property, int value) {
        if (value < 0) {
            throw invalid(property, "must not be negative");
        }
    }

    private static void requireNonNull(String property, Object value) {
        if (value == null) {
            throw invalid(property, "must not be null");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static IllegalStateException invalid(String property, String reason) {
        return new IllegalStateException("simple-secret.redis." + property + " " + reason);
    }
}

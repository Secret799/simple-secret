package com.ss.redis.config;

import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

import java.time.Duration;
import java.util.Objects;

/**
 * 将 {@link RedisProperties} 转换为 Redisson 原生 {@link Config}。
 */
public final class RedissonConfigFactory {

    /**
     * 创建经过校验的 Redisson 配置。
     *
     * @param properties Redis starter 配置
     * @return Redisson 原生配置
     */
    public Config create(RedisProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        if (!properties.isEnabled()) {
            throw new IllegalStateException("simple-secret.redis.enabled must be true to create a client config");
        }
        properties.validate();

        Config config = new Config()
                .setThreads(properties.getThreads())
                .setNettyThreads(properties.getNettyThreads())
                .setUseScriptCache(properties.isUseScriptCache());

        KeyPrefixMapper nameMapper = new KeyPrefixMapper(properties.getKeyPrefix());
        if (properties.getMode() == RedisMode.SINGLE) {
            configureSingle(config.useSingleServer(), properties.getSingle(), nameMapper);
        } else {
            configureCluster(config.useClusterServers(), properties.getCluster(), nameMapper);
        }
        return config;
    }

    private static void configureSingle(SingleServerConfig target,
                                        RedisProperties.Single source,
                                        KeyPrefixMapper nameMapper) {
        target.setAddress(source.getAddress())
                .setDatabase(source.getDatabase())
                .setConnectTimeout(toMillis(source.getConnectTimeout()))
                .setTimeout(toMillis(source.getTimeout()))
                .setIdleConnectionTimeout(toMillis(source.getIdleConnectionTimeout()))
                .setConnectionMinimumIdleSize(source.getConnectionMinimumIdleSize())
                .setConnectionPoolSize(source.getConnectionPoolSize())
                .setSubscriptionConnectionMinimumIdleSize(source.getSubscriptionConnectionMinimumIdleSize())
                .setSubscriptionConnectionPoolSize(source.getSubscriptionConnectionPoolSize())
                .setNameMapper(nameMapper);
        applyCredentials(target, source.getUsername(), source.getPassword(), source.getClientName());
    }

    private static void configureCluster(ClusterServersConfig target,
                                         RedisProperties.Cluster source,
                                         KeyPrefixMapper nameMapper) {
        target.addNodeAddress(source.getNodeAddresses().toArray(String[]::new))
                .setScanInterval(toMillis(source.getScanInterval()))
                .setConnectTimeout(toMillis(source.getConnectTimeout()))
                .setTimeout(toMillis(source.getTimeout()))
                .setIdleConnectionTimeout(toMillis(source.getIdleConnectionTimeout()))
                .setMasterConnectionMinimumIdleSize(source.getMasterConnectionMinimumIdleSize())
                .setMasterConnectionPoolSize(source.getMasterConnectionPoolSize())
                .setSlaveConnectionMinimumIdleSize(source.getSlaveConnectionMinimumIdleSize())
                .setSlaveConnectionPoolSize(source.getSlaveConnectionPoolSize())
                .setSubscriptionConnectionMinimumIdleSize(source.getSubscriptionConnectionMinimumIdleSize())
                .setSubscriptionConnectionPoolSize(source.getSubscriptionConnectionPoolSize())
                .setReadMode(source.getReadMode())
                .setSubscriptionMode(source.getSubscriptionMode())
                .setNameMapper(nameMapper);
        applyCredentials(target, source.getUsername(), source.getPassword(), source.getClientName());
    }

    private static void applyCredentials(org.redisson.config.BaseConfig<?> target,
                                         String username,
                                         String password,
                                         String clientName) {
        if (username != null) {
            target.setUsername(username);
        }
        if (password != null) {
            target.setPassword(password);
        }
        if (clientName != null) {
            target.setClientName(clientName);
        }
    }

    private static int toMillis(Duration duration) {
        return Math.toIntExact(duration.toMillis());
    }
}

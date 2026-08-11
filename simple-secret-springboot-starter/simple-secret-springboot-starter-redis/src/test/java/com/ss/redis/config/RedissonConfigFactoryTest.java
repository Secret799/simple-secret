package com.ss.redis.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SingleServerConfig;
import org.redisson.config.SubscriptionMode;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedissonConfigFactoryTest {

    private final RedissonConfigFactory factory = new RedissonConfigFactory();

    @Test
    void buildsSingleServerConfiguration() {
        RedisProperties properties = baseProperties(RedisMode.SINGLE);
        RedisProperties.Single single = properties.getSingle();
        single.setAddress("rediss://redis.example:6380");
        single.setUsername("app-user");
        single.setPassword("app-password");
        single.setClientName("orders-service");
        single.setDatabase(3);
        single.setConnectTimeout(Duration.ofSeconds(8));
        single.setTimeout(Duration.ofSeconds(2));
        single.setIdleConnectionTimeout(Duration.ofSeconds(12));
        single.setConnectionMinimumIdleSize(4);
        single.setConnectionPoolSize(12);
        single.setSubscriptionConnectionMinimumIdleSize(2);
        single.setSubscriptionConnectionPoolSize(6);

        Config config = factory.create(properties);
        SingleServerConfig actual = config.useSingleServer();

        assertThat(config.isSingleConfig()).isTrue();
        assertThat(config.isClusterConfig()).isFalse();
        assertThat(config.getThreads()).isEqualTo(6);
        assertThat(config.getNettyThreads()).isEqualTo(10);
        assertThat(config.isUseScriptCache()).isFalse();
        assertThat(actual.getAddress()).isEqualTo("rediss://redis.example:6380");
        assertThat(actual.getUsername()).isEqualTo("app-user");
        assertThat(actual.getPassword()).isEqualTo("app-password");
        assertThat(actual.getClientName()).isEqualTo("orders-service");
        assertThat(actual.getDatabase()).isEqualTo(3);
        assertThat(actual.getConnectTimeout()).isEqualTo(8_000);
        assertThat(actual.getTimeout()).isEqualTo(2_000);
        assertThat(actual.getIdleConnectionTimeout()).isEqualTo(12_000);
        assertThat(actual.getConnectionMinimumIdleSize()).isEqualTo(4);
        assertThat(actual.getConnectionPoolSize()).isEqualTo(12);
        assertThat(actual.getSubscriptionConnectionMinimumIdleSize()).isEqualTo(2);
        assertThat(actual.getSubscriptionConnectionPoolSize()).isEqualTo(6);
        assertThat(actual.getNameMapper().map("orders")).isEqualTo("tenant:orders");
    }

    @Test
    void buildsClusterServerConfiguration() {
        RedisProperties properties = baseProperties(RedisMode.CLUSTER);
        RedisProperties.Cluster cluster = properties.getCluster();
        cluster.setNodeAddresses(List.of(
                "redis://redis-1.example:6379",
                "rediss://redis-2.example:6380"));
        cluster.setUsername("cluster-user");
        cluster.setPassword("cluster-password");
        cluster.setClientName("analytics-service");
        cluster.setConnectTimeout(Duration.ofSeconds(7));
        cluster.setTimeout(Duration.ofSeconds(4));
        cluster.setIdleConnectionTimeout(Duration.ofSeconds(11));
        cluster.setScanInterval(Duration.ofSeconds(6));
        cluster.setMasterConnectionMinimumIdleSize(3);
        cluster.setMasterConnectionPoolSize(9);
        cluster.setSlaveConnectionMinimumIdleSize(5);
        cluster.setSlaveConnectionPoolSize(15);
        cluster.setSubscriptionConnectionMinimumIdleSize(2);
        cluster.setSubscriptionConnectionPoolSize(8);
        cluster.setReadMode(ReadMode.MASTER_SLAVE);
        cluster.setSubscriptionMode(SubscriptionMode.SLAVE);

        Config config = factory.create(properties);
        ClusterServersConfig actual = config.useClusterServers();

        assertThat(config.isClusterConfig()).isTrue();
        assertThat(config.isSingleConfig()).isFalse();
        assertThat(actual.getNodeAddresses()).containsExactlyElementsOf(cluster.getNodeAddresses());
        assertThat(actual.getUsername()).isEqualTo("cluster-user");
        assertThat(actual.getPassword()).isEqualTo("cluster-password");
        assertThat(actual.getClientName()).isEqualTo("analytics-service");
        assertThat(actual.getConnectTimeout()).isEqualTo(7_000);
        assertThat(actual.getTimeout()).isEqualTo(4_000);
        assertThat(actual.getIdleConnectionTimeout()).isEqualTo(11_000);
        assertThat(actual.getScanInterval()).isEqualTo(6_000);
        assertThat(actual.getMasterConnectionMinimumIdleSize()).isEqualTo(3);
        assertThat(actual.getMasterConnectionPoolSize()).isEqualTo(9);
        assertThat(actual.getSlaveConnectionMinimumIdleSize()).isEqualTo(5);
        assertThat(actual.getSlaveConnectionPoolSize()).isEqualTo(15);
        assertThat(actual.getSubscriptionConnectionMinimumIdleSize()).isEqualTo(2);
        assertThat(actual.getSubscriptionConnectionPoolSize()).isEqualTo(8);
        assertThat(actual.getReadMode()).isEqualTo(ReadMode.MASTER_SLAVE);
        assertThat(actual.getSubscriptionMode()).isEqualTo(SubscriptionMode.SLAVE);
        assertThat(actual.getNameMapper().map("metrics")).isEqualTo("tenant:metrics");
    }

    @Test
    void rejectsDisabledOrInvalidConfiguration() {
        RedisProperties disabled = new RedisProperties();

        assertThatThrownBy(() -> factory.create(disabled))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enabled");

        RedisProperties invalid = baseProperties(RedisMode.SINGLE);

        assertThatThrownBy(() -> factory.create(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("single.address");
    }

    private static RedisProperties baseProperties(RedisMode mode) {
        RedisProperties properties = new RedisProperties();
        properties.setEnabled(true);
        properties.setMode(mode);
        properties.setKeyPrefix("tenant");
        properties.setThreads(6);
        properties.setNettyThreads(10);
        properties.setUseScriptCache(false);
        return properties;
    }
}

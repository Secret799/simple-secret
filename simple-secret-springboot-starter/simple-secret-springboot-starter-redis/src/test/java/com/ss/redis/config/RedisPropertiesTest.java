package com.ss.redis.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.ReadMode;
import org.redisson.config.SubscriptionMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisPropertiesTest {

    @Test
    void isDisabledAndDoesNotRequireAnAddressByDefault() {
        RedisProperties properties = new RedisProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getMode()).isEqualTo(RedisMode.SINGLE);
        assertThat(properties.getKeyPrefix()).isEmpty();
        assertThat(properties.getCache().isEnabled()).isFalse();
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void validatesOnlyTheSelectedConnectionMode() {
        RedisProperties properties = enabledProperties();
        properties.getSingle().setAddress("redis://localhost:6379");

        assertThatCode(properties::validate).doesNotThrowAnyException();

        properties.setMode(RedisMode.CLUSTER);
        properties.getCluster().setNodeAddresses(List.of(
                "redis://redis-1.example:6379",
                "rediss://redis-2.example:6380"));
        properties.getSingle().setAddress(null);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void requiresAnAddressForTheSelectedMode() {
        RedisProperties properties = enabledProperties();

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("single.address");

        properties.setMode(RedisMode.CLUSTER);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cluster.node-addresses");
    }

    @Test
    void acceptsOnlyRedisProtocols() {
        RedisProperties properties = enabledProperties();
        properties.getSingle().setAddress("http://localhost:6379");

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redis://")
                .hasMessageContaining("rediss://");

        properties.getSingle().setAddress("rediss://localhost:6380");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingModeAndNonPositiveCommonValues() {
        RedisProperties properties = enabledProperties();
        properties.getSingle().setAddress("redis://localhost:6379");
        properties.setMode(null);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mode");

        properties.setMode(RedisMode.SINGLE);
        properties.setThreads(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("threads");

        properties.setThreads(4);
        properties.setNettyThreads(-1);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("netty-threads");
    }

    @Test
    void rejectsNonPositiveTimeoutsAndInvalidPoolSizes() {
        RedisProperties properties = enabledProperties();
        RedisProperties.Single single = properties.getSingle();
        single.setAddress("redis://localhost:6379");
        single.setTimeout(Duration.ZERO);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("single.timeout");

        single.setTimeout(Duration.ofSeconds(3));
        single.setConnectionMinimumIdleSize(5);
        single.setConnectionPoolSize(4);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection-pool-size");
    }

    @Test
    void copiesClusterNodeAddressesAndExposesAnImmutableView() {
        RedisProperties.Cluster cluster = new RedisProperties.Cluster();
        List<String> nodes = new ArrayList<>(List.of("redis://redis-1.example:6379"));

        cluster.setNodeAddresses(nodes);
        nodes.add("redis://redis-2.example:6379");

        assertThat(cluster.getNodeAddresses()).containsExactly("redis://redis-1.example:6379");
        assertThatThrownBy(() -> cluster.getNodeAddresses().add("redis://redis-3.example:6379"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void providesSecureDefaultsForClusterRouting() {
        RedisProperties.Cluster cluster = new RedisProperties.Cluster();

        assertThat(cluster.getReadMode()).isEqualTo(ReadMode.SLAVE);
        assertThat(cluster.getSubscriptionMode()).isEqualTo(SubscriptionMode.MASTER);
    }

    @Test
    void neverRendersCredentialsInStringRepresentations() {
        RedisProperties properties = enabledProperties();
        properties.getSingle().setAddress("redis://localhost:6379");
        properties.getSingle().setUsername("redis-user");
        properties.getSingle().setPassword("single-secret");
        properties.getCluster().setUsername("cluster-user");
        properties.getCluster().setPassword("cluster-secret");

        assertThat(properties.toString())
                .doesNotContain("redis-user")
                .doesNotContain("single-secret")
                .doesNotContain("cluster-user")
                .doesNotContain("cluster-secret");
        assertThat(properties.getSingle().toString()).doesNotContain("single-secret");
        assertThat(properties.getCluster().toString()).doesNotContain("cluster-secret");
    }

    private static RedisProperties enabledProperties() {
        RedisProperties properties = new RedisProperties();
        properties.setEnabled(true);
        return properties;
    }
}

package com.ss.redis.config;

import com.ss.redis.operation.RedissonOperations;
import com.ss.redis.operation.RedissonQueueOperations;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.function.Function;

/**
 * Simple Secret Redis 自动配置。
 *
 * <p>默认不连接 Redis。调用方可以提供自己的 {@link RedissonClient}，此时 starter
 * 只创建操作门面且不接管客户端生命周期。</p>
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@EnableConfigurationProperties(RedisProperties.class)
public class SimpleSecretRedisAutoConfiguration {

    /** 创建默认 Redisson 客户端工厂。 */
    @Bean(name = "simpleSecretRedissonClientFactory")
    @ConditionalOnMissingBean(name = "simpleSecretRedissonClientFactory")
    @ConditionalOnProperty(prefix = "simple-secret.redis", name = "enabled", havingValue = "true")
    Function<Config, RedissonClient> simpleSecretRedissonClientFactory() {
        return Redisson::create;
    }

    /** 创建由 starter 管理生命周期的 Redisson 客户端。 */
    @Bean(name = "simpleSecretRedissonClient", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    @ConditionalOnProperty(prefix = "simple-secret.redis", name = "enabled", havingValue = "true")
    RedissonClient simpleSecretRedissonClient(
            RedisProperties properties,
            @Qualifier("simpleSecretRedissonClientFactory") Function<Config, RedissonClient> clientFactory) {
        Config config = new RedissonConfigFactory().create(properties);
        return clientFactory.apply(config);
    }

    /** 创建默认 Redis 操作门面。 */
    @Bean(name = "redissonOperations")
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(RedissonOperations.class)
    RedissonOperations redissonOperations(RedissonClient client) {
        return new RedissonOperations(client);
    }

    /** 创建默认 Redis 队列操作门面。 */
    @Bean(name = "redissonQueueOperations")
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(RedissonQueueOperations.class)
    RedissonQueueOperations redissonQueueOperations(RedissonClient client) {
        return new RedissonQueueOperations(client);
    }
}

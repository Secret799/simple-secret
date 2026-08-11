package com.ss.redis.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 显式启用的 Redisson Spring Cache 自动配置。
 */
@AutoConfiguration(after = SimpleSecretRedisAutoConfiguration.class)
@ConditionalOnClass({CacheManager.class, RedissonSpringCacheManager.class})
@ConditionalOnBean(RedissonClient.class)
@ConditionalOnProperty(prefix = "simple-secret.redis.cache", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RedisProperties.class)
@EnableCaching
public class RedisCacheConfiguration {

    /** 创建官方 Redisson Spring CacheManager。 */
    @Bean(name = "redisCacheManager")
    @ConditionalOnMissingBean(CacheManager.class)
    RedissonSpringCacheManager redisCacheManager(
            RedissonClient client, RedisProperties properties) {
        RedisProperties.Cache cache = properties.getCache();
        if (cache == null) {
            throw new IllegalStateException("simple-secret.redis.cache must not be null");
        }
        Map<String, CacheConfig> configs = toCacheConfigs(cache);
        RedissonSpringCacheManager manager = new RedissonSpringCacheManager(client, configs);
        manager.setAllowNullValues(cache.isAllowNullValues());
        manager.setTransactionAware(cache.isTransactionAware());
        manager.setCacheNames(configs.keySet());
        return manager;
    }

    static Map<String, CacheConfig> toCacheConfigs(RedisProperties.Cache cache) {
        cache.validate();
        Map<String, CacheConfig> configs = new LinkedHashMap<>();
        cache.getCaches().forEach((name, spec) -> {
            CacheConfig config = new CacheConfig(
                    spec.getTtl().toMillis(), spec.getMaxIdleTime().toMillis());
            config.setMaxSize(spec.getMaxSize());
            configs.put(name, config);
        });
        return Map.copyOf(configs);
    }
}

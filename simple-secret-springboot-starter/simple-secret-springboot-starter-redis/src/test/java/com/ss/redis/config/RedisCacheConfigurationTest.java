package com.ss.redis.config;

import org.junit.jupiter.api.Test;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisCacheConfigurationTest {

    private static final RedissonClient CLIENT = mock(RedissonClient.class);
    private static final RMapCache<Object, Object> USERS_CACHE = mock(RMapCache.class);
    private static final CacheManager CALLER_CACHE_MANAGER = mock(CacheManager.class);

    static {
        when(CLIENT.getMapCache("users")).thenReturn(USERS_CACHE);
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretRedisAutoConfiguration.class,
                    RedisCacheConfiguration.class));

    @Test
    void doesNotEnableSpringCacheByDefault() {
        runner.withUserConfiguration(ClientConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(CacheManager.class));
    }

    @Test
    void createsOfficialRedissonCacheManagerOnlyWhenExplicitlyEnabled() {
        runner.withUserConfiguration(ClientConfiguration.class)
                .withPropertyValues(
                        "simple-secret.redis.cache.enabled=true",
                        "simple-secret.redis.cache.allow-null-values=false",
                        "simple-secret.redis.cache.transaction-aware=true",
                        "simple-secret.redis.cache.caches.users.ttl=30s",
                        "simple-secret.redis.cache.caches.users.max-idle-time=5s",
                        "simple-secret.redis.cache.caches.users.max-size=100")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context).hasBean("redisCacheManager");
                    RedissonSpringCacheManager manager = context.getBean(
                            "redisCacheManager", RedissonSpringCacheManager.class);
                    assertThat(manager.getCacheNames()).containsExactly("users");
                    assertThat(readBoolean(manager, "allowNullValues")).isFalse();
                    assertThat(readBoolean(manager, "transactionAware")).isTrue();

                    CacheConfig users = readConfigMap(manager).get("users");
                    assertThat(users.getTTL()).isEqualTo(30_000L);
                    assertThat(users.getMaxIdleTime()).isEqualTo(5_000L);
                    assertThat(users.getMaxSize()).isEqualTo(100);
                });
    }

    @Test
    void convertsEveryConfiguredCacheWithoutDynamicNameParsing() {
        RedisProperties.Cache cache = new RedisProperties.Cache();
        RedisProperties.CacheSpec users = new RedisProperties.CacheSpec();
        users.setTtl(Duration.ofMinutes(2));
        users.setMaxIdleTime(Duration.ofSeconds(15));
        users.setMaxSize(250);
        RedisProperties.CacheSpec permanent = new RedisProperties.CacheSpec();
        cache.setCaches(Map.of("users", users, "permanent", permanent));

        Map<String, CacheConfig> configs = RedisCacheConfiguration.toCacheConfigs(cache);

        assertThat(configs).containsOnlyKeys("users", "permanent");
        assertThat(configs.get("users").getTTL()).isEqualTo(120_000L);
        assertThat(configs.get("users").getMaxIdleTime()).isEqualTo(15_000L);
        assertThat(configs.get("users").getMaxSize()).isEqualTo(250);
        assertThat(configs.get("permanent").getTTL()).isZero();
        assertThat(configs.get("permanent").getMaxIdleTime()).isZero();
        assertThat(configs.get("permanent").getMaxSize()).isZero();
    }

    @Test
    void backsOffForCallerCacheManager() {
        runner.withUserConfiguration(ClientConfiguration.class, CallerCacheManagerConfiguration.class)
                .withPropertyValues("simple-secret.redis.cache.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context.getBean(CacheManager.class)).isSameAs(CALLER_CACHE_MANAGER);
                    assertThat(context).doesNotHaveBean("redisCacheManager");
                });
    }

    @Test
    void requiresClientAndValidCacheDefinitions() {
        runner.withPropertyValues("simple-secret.redis.cache.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(CacheManager.class));

        runner.withUserConfiguration(ClientConfiguration.class)
                .withPropertyValues(
                        "simple-secret.redis.cache.enabled=true",
                        "simple-secret.redis.cache.caches.users.ttl=-1s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessageContaining("cache.caches.users.ttl");
                });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CacheConfig> readConfigMap(RedissonSpringCacheManager manager) {
        return (Map<String, CacheConfig>) readField(manager, "configMap");
    }

    private static boolean readBoolean(RedissonSpringCacheManager manager, String fieldName) {
        return (boolean) readField(manager, fieldName);
    }

    private static Object readField(RedissonSpringCacheManager manager, String fieldName) {
        try {
            Field field = RedissonSpringCacheManager.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(manager);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Redisson cache manager", exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ClientConfiguration {

        @Bean(destroyMethod = "")
        RedissonClient redissonClient() {
            return CLIENT;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CallerCacheManagerConfiguration {

        @Bean
        CacheManager callerCacheManager() {
            return CALLER_CACHE_MANAGER;
        }
    }
}

package com.ss.redis.config;

import com.ss.redis.operation.RedissonOperations;
import com.ss.redis.operation.RedissonQueueOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

class SimpleSecretRedisAutoConfigurationTest {

    private static final RedissonClient STARTER_CLIENT = mock(RedissonClient.class);
    private static final RedissonClient CALLER_CLIENT = mock(RedissonClient.class);
    private static final AtomicInteger FACTORY_CALLS = new AtomicInteger();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretRedisAutoConfiguration.class));

    @BeforeEach
    void resetTestDoubles() {
        reset(STARTER_CLIENT, CALLER_CLIENT);
        FACTORY_CALLS.set(0);
    }

    @Test
    void doesNotCreateAClientOrOperationsByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RedisProperties.class);
            assertThat(context).doesNotHaveBean(RedissonClient.class);
            assertThat(context).doesNotHaveBean(RedissonOperations.class);
            assertThat(context).doesNotHaveBean(RedissonQueueOperations.class);
            assertThat(context).doesNotHaveBean(CacheManager.class);
        });
    }

    @Test
    void createsAndOwnsClientOnlyWhenExplicitlyEnabled() {
        runner.withUserConfiguration(ClientFactoryConfiguration.class)
                .withPropertyValues(
                        "simple-secret.redis.enabled=true",
                        "simple-secret.redis.single.address=redis://localhost:6379")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedissonClient.class);
                    assertThat(context.getBean("simpleSecretRedissonClient"))
                            .isSameAs(STARTER_CLIENT);
                    assertThat(context).hasSingleBean(RedissonOperations.class);
                    assertThat(context).hasSingleBean(RedissonQueueOperations.class);
                    assertThat(context).hasBean("redissonOperations");
                    assertThat(context).hasBean("redissonQueueOperations");
                    assertThat(context).doesNotHaveBean(CacheManager.class);
                    assertThat(FACTORY_CALLS).hasValue(1);
                });

        verify(STARTER_CLIENT).shutdown();
    }

    @Test
    void backsOffForCallerClientAndLeavesItsLifecycleToTheCaller() {
        runner.withUserConfiguration(CallerClientConfiguration.class, ClientFactoryConfiguration.class)
                .withPropertyValues("simple-secret.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedissonClient.class);
                    assertThat(context.getBean(RedissonClient.class)).isSameAs(CALLER_CLIENT);
                    assertThat(context).doesNotHaveBean("simpleSecretRedissonClient");
                    assertThat(context).hasSingleBean(RedissonOperations.class);
                    assertThat(context).hasSingleBean(RedissonQueueOperations.class);
                    assertThat(FACTORY_CALLS).hasValue(0);
                });

        verify(CALLER_CLIENT, never()).shutdown();
    }

    @Configuration(proxyBeanMethods = false)
    static class ClientFactoryConfiguration {

        @Bean(name = "simpleSecretRedissonClientFactory")
        Function<Config, RedissonClient> simpleSecretRedissonClientFactory() {
            return config -> {
                FACTORY_CALLS.incrementAndGet();
                return STARTER_CLIENT;
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CallerClientConfiguration {

        @Bean(destroyMethod = "")
        RedissonClient callerRedissonClient() {
            return CALLER_CLIENT;
        }
    }
}

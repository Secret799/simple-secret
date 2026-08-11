package com.ss.idempotent.config;

import com.ss.idempotent.aspect.RepeatSubmitAspect;
import com.ss.idempotent.key.IdempotencyKeyGenerator;
import com.ss.idempotent.key.RequestIdentityResolver;
import com.ss.idempotent.store.IdempotencyStore;
import com.ss.idempotent.store.RedissonIdempotencyStore;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 验证自动配置默认失败关闭，并允许消费者替换所有策略接口。 */
class SimpleSecretIdempotentAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretIdempotentAutoConfiguration.class));

    @Test
    void shouldCreateDefaultInfrastructureWithCustomStore() {
        runner.withUserConfiguration(StoreConfiguration.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(IdempotentProperties.class)
                        .hasSingleBean(RequestIdentityResolver.class)
                        .hasSingleBean(IdempotencyKeyGenerator.class)
                        .hasSingleBean(RepeatSubmitAspect.class)
                        .hasSingleBean(IdempotencyStore.class));
    }

    @Test
    void shouldBackOffForConsumerStrategies() {
        runner.withUserConfiguration(OverrideConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RequestIdentityResolver.class))
                            .isSameAs(context.getBean("customIdentityResolver"));
                    assertThat(context.getBean(IdempotencyKeyGenerator.class))
                            .isSameAs(context.getBean("customKeyGenerator"));
                });
    }

    @Test
    void shouldAdaptExistingRedissonClient() {
        runner.withUserConfiguration(RedissonConfiguration.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(RedissonClient.class)
                        .hasSingleBean(RedissonIdempotencyStore.class)
                        .hasSingleBean(RepeatSubmitAspect.class));
    }

    @Test
    void shouldHonorFeatureSwitch() {
        runner.withPropertyValues("simple-secret.idempotent.enabled=false")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(IdempotentProperties.class)
                        .doesNotHaveBean(RepeatSubmitAspect.class));
    }

    @Test
    void shouldFailStartupWhenEnabledWithoutStore() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasMessageContaining("IdempotencyStore");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class StoreConfiguration {

        @Bean
        IdempotencyStore idempotencyStore() {
            return new IdempotencyStore() {
                @Override
                public boolean tryAcquire(String key, String owner, Duration ttl) {
                    return true;
                }

                @Override
                public boolean release(String key, String owner) {
                    return true;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OverrideConfiguration extends StoreConfiguration {

        @Bean
        RequestIdentityResolver customIdentityResolver() {
            return request -> "custom";
        }

        @Bean
        IdempotencyKeyGenerator customKeyGenerator() {
            return (method, args, request) -> "custom-key";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RedissonConfiguration {

        @Bean
        RedissonClient redissonClient() {
            return mock(RedissonClient.class);
        }
    }
}

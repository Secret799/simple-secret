package com.ss.consumer.redis;

import com.ss.redis.operation.RedissonOperations;
import com.ss.redis.operation.RedissonQueueOperations;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the Redis starter from an otherwise empty consumer project. */
class RedisStarterConsumerTest {

    private static final RedissonClient CALLER_CLIENT = createCallerClient();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void doesNotCreateAnImplicitRedisConnection() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RedissonClient.class);
            assertThat(context).doesNotHaveBean(RedissonOperations.class);
            assertThat(context).doesNotHaveBean(RedissonQueueOperations.class);
        });
    }

    @Test
    void createsOperationFacadesForCallerOwnedClient() {
        runner.withUserConfiguration(CallerClientConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedissonClient.class);
                    assertThat(context.getBean(RedissonClient.class)).isSameAs(CALLER_CLIENT);
                    assertThat(context).hasSingleBean(RedissonOperations.class);
                    assertThat(context).hasSingleBean(RedissonQueueOperations.class);
                    assertThat(context).hasBean("redissonOperations");
                    assertThat(context).hasBean("redissonQueueOperations");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class CallerClientConfiguration {

        @Bean(destroyMethod = "")
        RedissonClient redissonClient() {
            return CALLER_CLIENT;
        }
    }

    private static RedissonClient createCallerClient() {
        return (RedissonClient) Proxy.newProxyInstance(
                RedissonClient.class.getClassLoader(),
                new Class<?>[]{RedissonClient.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "callerRedissonClient";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    throw new UnsupportedOperationException(
                            "Unexpected RedissonClient call: " + method.getName());
                });
    }
}

package com.ss.core.config;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 core starter 的默认行为、属性绑定和配置校验。 */
class CoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretCoreAutoConfiguration.class));

    @Test
    void shouldHaveNoRuntimeSideEffectsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CoreProperties.class);
            assertThat(context).doesNotHaveBean(Executor.class);
            assertThat(context).doesNotHaveBean(ScheduledExecutorService.class);
            assertThat(context).doesNotHaveBean(AsyncConfigurer.class);
            assertThat(context).doesNotHaveBean(Validator.class);
        });
    }

    @Test
    void shouldBindProjectAndFeatureProperties() {
        contextRunner.withPropertyValues(
                        "simple-secret.core.project.name=demo",
                        "simple-secret.core.project.description=Demo service",
                        "simple-secret.core.project.version=1.2.0",
                        "simple-secret.core.project.copyright-year=2026",
                        "simple-secret.core.task-executor.core-pool-size=3",
                        "simple-secret.core.task-executor.max-pool-size=6",
                        "simple-secret.core.task-executor.queue-capacity=128",
                        "simple-secret.core.task-executor.keep-alive=30s",
                        "simple-secret.core.task-executor.thread-name-prefix=worker-",
                        "simple-secret.core.scheduler.pool-size=2",
                        "simple-secret.core.scheduler.thread-name-prefix=timer-")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CoreProperties properties = context.getBean(CoreProperties.class);
                    assertThat(properties.getProject().getName()).isEqualTo("demo");
                    assertThat(properties.getProject().getDescription()).isEqualTo("Demo service");
                    assertThat(properties.getProject().getVersion()).isEqualTo("1.2.0");
                    assertThat(properties.getProject().getCopyrightYear()).isEqualTo(2026);
                    assertThat(properties.getTaskExecutor().getCorePoolSize()).isEqualTo(3);
                    assertThat(properties.getTaskExecutor().getMaxPoolSize()).isEqualTo(6);
                    assertThat(properties.getTaskExecutor().getQueueCapacity()).isEqualTo(128);
                    assertThat(properties.getTaskExecutor().getKeepAlive()).hasSeconds(30);
                    assertThat(properties.getTaskExecutor().getThreadNamePrefix()).isEqualTo("worker-");
                    assertThat(properties.getScheduler().getPoolSize()).isEqualTo(2);
                    assertThat(properties.getScheduler().getThreadNamePrefix()).isEqualTo("timer-");
                });
    }

    @Test
    void shouldRejectInvalidEnabledTaskExecutorProperties() {
        assertInvalid("simple-secret.core.task-executor.enabled=true",
                "simple-secret.core.task-executor.core-pool-size=0", "core-pool-size");
        assertInvalid("simple-secret.core.task-executor.enabled=true",
                "simple-secret.core.task-executor.core-pool-size=4",
                "simple-secret.core.task-executor.max-pool-size=3", "max-pool-size");
        assertInvalid("simple-secret.core.task-executor.enabled=true",
                "simple-secret.core.task-executor.queue-capacity=-1", "queue-capacity");
        assertInvalid("simple-secret.core.task-executor.enabled=true",
                "simple-secret.core.task-executor.keep-alive=-1s", "keep-alive");
        assertInvalid("simple-secret.core.task-executor.enabled=true",
                "simple-secret.core.task-executor.keep-alive=2147483648s", "keep-alive");
        assertInvalid("simple-secret.core.task-executor.enabled=true",
                "simple-secret.core.task-executor.thread-name-prefix= ", "thread-name-prefix");
    }

    @Test
    void shouldRejectInvalidEnabledSchedulerProperties() {
        assertInvalid("simple-secret.core.scheduler.enabled=true",
                "simple-secret.core.scheduler.pool-size=0", "pool-size");
        assertInvalid("simple-secret.core.scheduler.enabled=true",
                "simple-secret.core.scheduler.thread-name-prefix= ", "thread-name-prefix");
    }

    private void assertInvalid(String propertyOne, String propertyTwo, String expectedMessage) {
        contextRunner.withPropertyValues(propertyOne, propertyTwo).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining(expectedMessage);
        });
    }

    private void assertInvalid(
            String propertyOne, String propertyTwo, String propertyThree, String expectedMessage) {
        contextRunner.withPropertyValues(propertyOne, propertyTwo, propertyThree).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining(expectedMessage);
        });
    }
}

package com.ss.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证可选任务执行器与调度器的创建、覆盖和生命周期。 */
class CoreExecutorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretCoreAutoConfiguration.class,
                    CoreExecutorAutoConfiguration.class));

    @Test
    void shouldCreateOnlyEnabledTaskExecutorWithConfiguredThreadName() {
        contextRunner.withPropertyValues(
                        "simple-secret.core.task-executor.enabled=true",
                        "simple-secret.core.task-executor.core-pool-size=1",
                        "simple-secret.core.task-executor.max-pool-size=1",
                        "simple-secret.core.task-executor.queue-capacity=1",
                        "simple-secret.core.task-executor.thread-name-prefix=worker-")
                .run(context -> {
                    assertThat(context).hasBean("simpleSecretTaskExecutor");
                    assertThat(context).doesNotHaveBean("simpleSecretScheduledExecutorService");
                    ThreadPoolTaskExecutor executor = context.getBean(
                            "simpleSecretTaskExecutor", ThreadPoolTaskExecutor.class);
                    assertThat(executor.submit(() -> Thread.currentThread().getName()).get(2, TimeUnit.SECONDS))
                            .startsWith("worker-");
                });
    }

    @Test
    void shouldCreateOnlyEnabledSchedulerAndExposeTaskFailures() {
        contextRunner.withPropertyValues(
                        "simple-secret.core.scheduler.enabled=true",
                        "simple-secret.core.scheduler.pool-size=1",
                        "simple-secret.core.scheduler.thread-name-prefix=timer-")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("simpleSecretTaskExecutor");
                    ScheduledExecutorService scheduler = context.getBean(
                            "simpleSecretScheduledExecutorService", ScheduledExecutorService.class);
                    assertThat(scheduler.submit(() -> Thread.currentThread().getName()).get(2, TimeUnit.SECONDS))
                            .startsWith("timer-");
                    assertThat(scheduler.submit(() -> {
                        throw new IllegalStateException("scheduled failure");
                    })).failsWithin(2, TimeUnit.SECONDS)
                            .withThrowableOfType(java.util.concurrent.ExecutionException.class)
                            .withCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void shouldBackOffForConsumerBeansWithPublishedNames() {
        contextRunner.withUserConfiguration(UserExecutorConfiguration.class)
                .withPropertyValues(
                        "simple-secret.core.task-executor.enabled=true",
                        "simple-secret.core.scheduler.enabled=true")
                .run(context -> {
                    assertThat(context.getBean("simpleSecretTaskExecutor"))
                            .isSameAs(UserExecutorConfiguration.TASK_EXECUTOR);
                    assertThat(context.getBean("simpleSecretScheduledExecutorService"))
                            .isSameAs(UserExecutorConfiguration.SCHEDULER);
                });
    }

    @Test
    void shouldShutdownExecutorsWhenApplicationContextCloses() {
        AtomicReference<ThreadPoolTaskExecutor> taskExecutor = new AtomicReference<>();
        AtomicReference<ScheduledExecutorService> scheduler = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> delayedTask = new AtomicReference<>();
        AtomicBoolean delayedTaskRan = new AtomicBoolean();

        contextRunner.withPropertyValues(
                        "simple-secret.core.task-executor.enabled=true",
                        "simple-secret.core.scheduler.enabled=true")
                .run(context -> {
                    taskExecutor.set(context.getBean(
                            "simpleSecretTaskExecutor", ThreadPoolTaskExecutor.class));
                    scheduler.set(context.getBean(
                            "simpleSecretScheduledExecutorService", ScheduledExecutorService.class));
                    delayedTask.set(scheduler.get().schedule(
                            () -> delayedTaskRan.set(true), 1, TimeUnit.HOURS));
                });

        assertThat(taskExecutor.get().getThreadPoolExecutor().isShutdown()).isTrue();
        assertThat(scheduler.get().isShutdown()).isTrue();
        assertThat(delayedTask.get().isCancelled()).isTrue();
        assertThat(delayedTaskRan).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    static class UserExecutorConfiguration {
        private static final Executor TASK_EXECUTOR = Runnable::run;
        private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

        @Bean(name = "simpleSecretTaskExecutor")
        Executor taskExecutor() {
            return TASK_EXECUTOR;
        }

        @Bean(name = "simpleSecretScheduledExecutorService", destroyMethod = "shutdownNow")
        ScheduledExecutorService scheduler() {
            return SCHEDULER;
        }
    }
}

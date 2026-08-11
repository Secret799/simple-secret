package com.ss.consumer.core;

import com.ss.core.config.CoreProperties;
import com.ss.core.domain.Result;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ClassUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证第三方应用仅依赖 core starter 时的发布行为。 */
class CoreStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void shouldDiscoverStarterWithoutOptionalFrameworkDependenciesOrRuntimeBeans() throws Exception {
        assertThat(Result.ok("payload").getData()).isEqualTo("payload");
        assertThat(Files.readString(Path.of("../pom.xml")))
                .contains("<module>consumer-core</module>");

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CoreProperties.class);
            assertThat(context).doesNotHaveBean("simpleSecretTaskExecutor");
            assertThat(context).doesNotHaveBean("simpleSecretScheduledExecutorService");

            ConditionEvaluationReport report = ConditionEvaluationReport.get(context.getBeanFactory());
            assertThat(report.getConditionAndOutcomesBySource().keySet())
                    .anyMatch(source -> source.startsWith(
                            "com.ss.core.config.CoreExecutorAutoConfiguration"))
                    .contains("com.ss.core.config.CoreValidationAutoConfiguration");
            ClassLoader classLoader = getClass().getClassLoader();
            assertThat(ClassUtils.isPresent("jakarta.validation.Validator", classLoader)).isFalse();
            assertThat(ClassUtils.isPresent("org.hibernate.validator.HibernateValidator", classLoader)).isFalse();
            assertThat(ClassUtils.isPresent("jakarta.servlet.Servlet", classLoader)).isFalse();
            assertThat(ClassUtils.isPresent("javax.servlet.Servlet", classLoader)).isFalse();
        });
    }

    @Test
    void shouldCreateAndCloseExecutorOnlyWhenExplicitlyEnabled() {
        AtomicReference<ThreadPoolTaskExecutor> executorReference = new AtomicReference<>();
        runner.withPropertyValues(
                        "simple-secret.core.task-executor.enabled=true",
                        "simple-secret.core.task-executor.core-pool-size=1",
                        "simple-secret.core.task-executor.max-pool-size=1",
                        "simple-secret.core.task-executor.thread-name-prefix=consumer-")
                .run(context -> {
                    ThreadPoolTaskExecutor executor = context.getBean(
                            "simpleSecretTaskExecutor", ThreadPoolTaskExecutor.class);
                    executorReference.set(executor);
                    assertThat(executor.submit(() -> Thread.currentThread().getName()).get(2, TimeUnit.SECONDS))
                            .startsWith("consumer-");
                });

        assertThat(executorReference.get().getThreadPoolExecutor().isShutdown()).isTrue();
    }

    @Test
    void shouldPreferConsumerBeanWithPublishedName() {
        new ApplicationContextRunner()
                .withUserConfiguration(ConsumerApplication.class, ConsumerExecutorConfiguration.class)
                .withPropertyValues("simple-secret.core.task-executor.enabled=true")
                .run(context -> assertThat(context.getBean("simpleSecretTaskExecutor"))
                        .isSameAs(ConsumerExecutorConfiguration.EXECUTOR));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerExecutorConfiguration {
        private static final Executor EXECUTOR = Runnable::run;

        @Bean(name = "simpleSecretTaskExecutor")
        Executor executor() {
            return EXECUTOR;
        }
    }
}

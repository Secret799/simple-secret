package com.ss.core.config;

import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证可选异步与 fail-fast Bean Validation 配置。 */
class CoreAsyncAndValidationAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretCoreAutoConfiguration.class,
                    CoreExecutorAutoConfiguration.class,
                    CoreAsyncAutoConfiguration.class,
                    CoreValidationAutoConfiguration.class));

    @Test
    void shouldFailClearlyWhenAsyncIsEnabledWithoutNamedExecutor() {
        contextRunner.withPropertyValues("simple-secret.core.async.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("simpleSecretTaskExecutor");
                });
    }

    @Test
    void shouldRunAsyncMethodsOnConfiguredTaskExecutor() {
        contextRunner.withUserConfiguration(AsyncProbeConfiguration.class)
                .withPropertyValues(
                        "simple-secret.core.task-executor.enabled=true",
                        "simple-secret.core.task-executor.core-pool-size=1",
                        "simple-secret.core.task-executor.max-pool-size=1",
                        "simple-secret.core.async.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AsyncConfigurer.class);
                    String threadName = context.getBean(AsyncProbe.class)
                            .threadName().get(2, TimeUnit.SECONDS);
                    assertThat(threadName).startsWith("ss-task-");
                });
    }

    @Test
    void shouldBackOffWhenConsumerProvidesAsyncConfigurer() {
        contextRunner.withUserConfiguration(UserAsyncConfigurerConfiguration.class)
                .withPropertyValues("simple-secret.core.async.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AsyncConfigurer.class);
                    assertThat(context.getBean(AsyncConfigurer.class))
                            .isSameAs(UserAsyncConfigurerConfiguration.CONFIGURER);
                    assertThat(context).doesNotHaveBean(
                            TaskManagementConfigUtils.ASYNC_ANNOTATION_PROCESSOR_BEAN_NAME);
                });
    }

    @Test
    void shouldNotIncludeAsyncArgumentsInFailureLogMessage() throws Exception {
        String message = CoreAsyncAutoConfiguration.failureMessage(
                AsyncProbe.class.getMethod("acceptSecret", String.class));

        assertThat(message).contains("acceptSecret").doesNotContain("secret-token");
    }

    @Test
    void shouldCreateManagedFailFastValidatorOnlyWhenEnabled() {
        contextRunner.withPropertyValues("simple-secret.core.validation.fail-fast=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(Validator.class);
                    Validator validator = context.getBean(Validator.class);
                    assertThat(validator.validate(new InvalidBean())).hasSize(1);
                    assertThat(context.getBean(Validator.class))
                            .isInstanceOf(LocalValidatorFactoryBean.class);
                });
    }

    @Test
    void shouldBackOffWhenConsumerProvidesValidator() {
        contextRunner.withUserConfiguration(UserValidatorConfiguration.class)
                .withPropertyValues("simple-secret.core.validation.fail-fast=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(Validator.class);
                    assertThat(context.getBean(Validator.class))
                            .isSameAs(context.getBean("userValidator"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class AsyncProbeConfiguration {
        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    static class AsyncProbe {
        @Async
        public CompletableFuture<String> threadName() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }

        public void acceptSecret(String secret) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserAsyncConfigurerConfiguration {
        private static final AsyncConfigurer CONFIGURER = new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                return Runnable::run;
            }
        };

        @Bean
        AsyncConfigurer userAsyncConfigurer() {
            return CONFIGURER;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserValidatorConfiguration {
        @Bean
        LocalValidatorFactoryBean userValidator() {
            LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
            validator.setMessageInterpolator(new ParameterMessageInterpolator());
            return validator;
        }
    }

    static class InvalidBean {
        @NotBlank
        private String first;
        @NotBlank
        private String second;
    }
}

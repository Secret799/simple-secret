package com.ss.sensitive.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ss.sensitive.annotation.Sensitive;
import com.ss.sensitive.core.SensitiveService;
import com.ss.sensitive.core.SensitiveStrategy;
import com.ss.sensitive.jackson.SimpleSecretSensitiveModule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 sensitive 自动配置默认安全、可关闭并保留消费者 Jackson 模块。 */
class SimpleSecretSensitiveAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretSensitiveAutoConfiguration.class,
                    JacksonAutoConfiguration.class));

    @Test
    void shouldMaskByDefaultAndPreserveConsumerModules() {
        runner.withUserConfiguration(ConsumerModuleConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SensitiveService.class);
                    assertThat(context).hasSingleBean(SimpleSecretSensitiveModule.class);
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertThat(mapper.getRegisteredModuleIds())
                            .contains("simple-secret-sensitive", "consumer-marker");
                    assertThat(mapper.writeValueAsString(new Sample("18049531999")))
                            .contains("180****1999");
                });
    }

    @Test
    void shouldBackOffForConsumerServiceAndModule() {
        runner.withUserConfiguration(ConsumerOverrideConfiguration.class)
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);

                    assertThat(context.getBean(SensitiveService.class).isSensitive("", ""))
                            .isFalse();
                    assertThat(mapper.writeValueAsString(new Sample("18049531999")))
                            .contains("18049531999");
                });
    }

    @Test
    void shouldHonorFeatureSwitch() {
        runner.withPropertyValues("simple-secret.sensitive.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SensitiveService.class)
                        .doesNotHaveBean(SimpleSecretSensitiveModule.class));
    }

    private record Sample(
            @Sensitive(strategy = SensitiveStrategy.PHONE) String phone) {
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerModuleConfiguration {

        @Bean
        SimpleModule consumerModule() {
            return new SimpleModule("consumer-marker");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerOverrideConfiguration {

        @Bean
        SensitiveService sensitiveService() {
            return (roleKey, perms) -> false;
        }

        @Bean
        SimpleSecretSensitiveModule sensitiveModule(SensitiveService service) {
            return new SimpleSecretSensitiveModule(service);
        }
    }
}

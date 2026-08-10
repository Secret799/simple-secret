package com.ss.consumer.zlm4j;

import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.context.ZlmMediaContext;
import com.ss.zlm4j.security.MediaResourcePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies ZLM4J defaults without loading the native media library. */
class Zlm4jStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void loadsSafeDefaultsWithNativeServiceDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ZlmMediaProperties.class);
            assertThat(context).hasSingleBean(MediaResourcePolicy.class);
            assertThat(context).doesNotHaveBean(ZlmMediaContext.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}

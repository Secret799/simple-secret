package com.ss.consumer.easymedia;

import com.ss.easymedia.config.SimpleSecretEasyMediaAutoConfiguration;
import com.ss.zlm4j.context.ZlmMediaContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies EasyMedia discovery while all external media services remain disabled. */
class EasyMediaStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void discoversAutoConfigurationWithoutStartingNativeMedia() {
        runner.withPropertyValues(
                        "simple-secret.zlm4j.enabled=false",
                        "simple-secret.easymedia.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ZlmMediaContext.class);
                    ConditionEvaluationReport report = ConditionEvaluationReport.get(context.getBeanFactory());
                    assertThat(report.getConditionAndOutcomesBySource())
                            .containsKey(SimpleSecretEasyMediaAutoConfiguration.class.getName());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}

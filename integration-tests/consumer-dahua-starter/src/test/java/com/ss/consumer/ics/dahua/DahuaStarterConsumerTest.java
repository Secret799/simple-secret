package com.ss.consumer.ics.dahua;

import com.ss.ics.dahua.DahuaCameraSdkService;
import com.ss.ics.dahua.DahuaSdkOptions;
import com.ss.ics.dahua.config.DahuaCameraSdkAutoConfiguration;
import com.ss.ics.dahua.config.DahuaCameraSdkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the published Dahua starter while native SDK loading stays disabled. */
class DahuaStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void discoversDisabledAutoConfigurationAndPublishedSdkTypes() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DahuaCameraSdkProperties.class);
            assertThat(context).doesNotHaveBean(DahuaCameraSdkService.class);
            assertThat(ConditionEvaluationReport.get(context.getBeanFactory())
                    .getConditionAndOutcomesBySource())
                    .containsKey(DahuaCameraSdkAutoConfiguration.class.getName());
        });

        assertThat(DahuaSdkOptions.defaults(Path.of("vendor/dahua")))
                .isNotNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}

package com.ss.consumer.ics.hikvision;

import com.ss.ics.hikvision.HikvisionCameraSdkService;
import com.ss.ics.hikvision.HikvisionSdkOptions;
import com.ss.ics.hikvision.config.HikvisionCameraSdkAutoConfiguration;
import com.ss.ics.hikvision.config.HikvisionCameraSdkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the published Hikvision starter while native SDK loading stays disabled. */
class HikvisionStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void discoversDisabledAutoConfigurationAndPublishedSdkTypes() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(HikvisionCameraSdkProperties.class);
            assertThat(context).doesNotHaveBean(HikvisionCameraSdkService.class);
            assertThat(ConditionEvaluationReport.get(context.getBeanFactory())
                    .getConditionAndOutcomesBySource())
                    .containsKey(HikvisionCameraSdkAutoConfiguration.class.getName());
        });

        assertThat(HikvisionSdkOptions.defaults(Path.of("vendor/hikvision")))
                .isNotNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}

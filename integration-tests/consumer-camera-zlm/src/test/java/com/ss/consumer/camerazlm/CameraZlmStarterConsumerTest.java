package com.ss.consumer.camerazlm;

import com.ss.camerazlm.DahuaZlmStreamService;
import com.ss.camerazlm.config.DahuaZlmStreamAutoConfiguration;
import com.ss.camerazlm.config.SimpleSecretCameraZlmAutoConfiguration;
import com.ss.easymedia.h264.H264NakedFlowPushZlmManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies Camera-to-ZLM discovery while all native services remain disabled. */
class CameraZlmStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void discoversAutoConfigurationWithoutStartingNativeServices() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DahuaZlmStreamService.class);
            assertThat(context).doesNotHaveBean(H264NakedFlowPushZlmManager.class);
            assertThat(ConditionEvaluationReport.get(context.getBeanFactory())
                    .getConditionAndOutcomesBySource())
                    .containsKeys(
                            SimpleSecretCameraZlmAutoConfiguration.class.getName(),
                            DahuaZlmStreamAutoConfiguration.class.getName());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}

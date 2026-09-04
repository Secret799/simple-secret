package com.ss.consumer.dji;

import com.ss.dji.camera.config.DjiSeiProperties;
import com.ss.dji.camera.diagnostic.DjiSeiTrackCallback;
import com.ss.dji.camera.web.DjiSeiSseController;
import com.ss.dji.camera.web.DjiSeiSseService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies DJI auto-configuration without starting native media services. */
class DjiStarterConsumerTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class)
            .withPropertyValues(
                    "simple-secret.dji-sei.enabled=true",
                    "simple-secret.easymedia.enabled=false",
                    "simple-secret.zlm4j.enabled=false");

    @Test
    void discoversDjiDiagnosticsFromPublishedArtifacts() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DjiSeiProperties.class);
            assertThat(context).hasSingleBean(DjiSeiTrackCallback.class);
            assertThat(context).hasSingleBean(DjiSeiSseService.class);
            assertThat(context).hasSingleBean(DjiSeiSseController.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}

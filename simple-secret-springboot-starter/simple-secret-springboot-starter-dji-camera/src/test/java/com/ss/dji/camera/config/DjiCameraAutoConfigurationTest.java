package com.ss.dji.camera.config;

import com.ss.dji.camera.diagnostic.DjiSeiTrackCallback;
import com.ss.dji.camera.web.DjiSeiController;
import com.ss.dji.camera.web.DjiSeiEventHub;
import com.ss.easymedia.callback.TrackDelegateCallback;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** 大疆 Camera 集成工具自动配置测试。 */
class DjiCameraAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DjiCameraAutoConfiguration.class));

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DjiCameraAutoConfiguration.class));

    @Test
    void shouldNotCreateIntegrationBeansWhenDisabled() {
        webContextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TrackDelegateCallback.class);
            assertThat(context).doesNotHaveBean(DjiSeiEventHub.class);
            assertThat(context).doesNotHaveBean(DjiSeiController.class);
        });
    }

    @Test
    void shouldCreateTrackCallbackWithoutWebDiagnosticsInNonWebApplication() {
        contextRunner.withPropertyValues("simple-secret.dji-sei.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TrackDelegateCallback.class);
                    assertThat(context).hasSingleBean(DjiSeiTrackCallback.class);
                    assertThat(context).doesNotHaveBean(DjiSeiEventHub.class);
                    assertThat(context).doesNotHaveBean(DjiSeiController.class);
                });
    }

    @Test
    void shouldCreateTrackCallbackAndDiagnosticsInServletApplication() {
        webContextRunner.withPropertyValues("simple-secret.dji-sei.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DjiSeiTrackCallback.class);
                    assertThat(context).hasSingleBean(DjiSeiEventHub.class);
                    assertThat(context).hasSingleBean(DjiSeiController.class);
                });
    }

    @Test
    void shouldHonorOverriddenClockAndPayloadLimit() {
        Clock overriddenClock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC);
        contextRunner.withBean(Clock.class, () -> overriddenClock)
                .withPropertyValues(
                        "simple-secret.dji-sei.enabled=true",
                        "simple-secret.dji-sei.max-payload-bytes=2048")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Clock.class)).isSameAs(overriddenClock);
                    assertThat(context.getBean(DjiSeiProperties.class).getMaxPayloadBytes()).isEqualTo(2048);
                    assertThat(context).hasSingleBean(DjiSeiTrackCallback.class);
                });
    }
}

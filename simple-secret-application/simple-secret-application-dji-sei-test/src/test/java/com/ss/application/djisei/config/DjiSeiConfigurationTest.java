package com.ss.application.djisei.config;

import com.ss.application.djisei.diagnostic.DjiSeiTrackCallback;
import com.ss.easymedia.callback.TrackDelegateCallback;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DjiSeiConfigurationTest {

    /** 仅加载被测诊断配置的上下文运行器。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DjiSeiConfiguration.class);

    @Test
    void shouldNotCreateTrackCallbackWhenDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TrackDelegateCallback.class);
        });
    }

    @Test
    void shouldCreateTrackCallbackWhenEnabled() {
        contextRunner.withPropertyValues("simple-secret.dji-sei.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TrackDelegateCallback.class);
                    assertThat(context).hasSingleBean(DjiSeiTrackCallback.class);
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

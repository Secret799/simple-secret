package com.ss.gb28181.zlm;

import com.ss.gb28181.Gb28181Server;
import com.ss.gb28181.GbRtpTarget;
import com.ss.gb28181.zlm.autoconfigure.SimpleSecretGbZlmAutoConfiguration;
import com.ss.zlm4j.service.IZlmMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GbZlmAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretGbZlmAutoConfiguration.class));

    @Test
    void disabledWithoutAnyDependencies() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(GbZlmPlayer.class));
    }

    @Test
    void enabledRequiresDependenciesAndExplicitMediaConfiguration() {
        runner.withPropertyValues("simple-secret.gb28181-zlm.enabled=true")
                .run(context -> assertThat(context).hasFailed());
        dependencies().withPropertyValues("simple-secret.gb28181-zlm.enabled=true")
                .run(context -> assertThat(context).hasFailed());
        dependencies().withPropertyValues("simple-secret.gb28181-zlm.enabled=true",
                        "simple-secret.gb28181-zlm.advertised-address=192.0.2.1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void createsWithExplicitSettingsAndClosesPlayerWithContext() {
        GbZlmPlayer[] player = new GbZlmPlayer[1];
        dependencies().withPropertyValues("simple-secret.gb28181-zlm.enabled=true",
                "simple-secret.gb28181-zlm.advertised-address=192.0.2.1",
                "simple-secret.gb28181-zlm.transport=tcp-passive",
                "simple-secret.gb28181-zlm.max-sessions=2")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(GbZlmPlayer.class);
                    player[0] = context.getBean(GbZlmPlayer.class);
                });
        assertThat(player[0].play("34020000001320000001", "34020000001320000002"))
                .isCompletedExceptionally();
    }

    @Test
    void rejectsInvalidCapacityWithoutAllocatingMedia() {
        dependencies().withPropertyValues("simple-secret.gb28181-zlm.enabled=true",
                        "simple-secret.gb28181-zlm.advertised-address=192.0.2.1",
                        "simple-secret.gb28181-zlm.transport=udp",
                        "simple-secret.gb28181-zlm.max-sessions=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void hostOverrideBacksOffWithoutValidatingUnusedProperties() {
        try (GbZlmPlayer custom = new GbZlmPlayer(mock(Gb28181Server.class),
                mock(IZlmMediaService.class), "192.0.2.1", GbRtpTarget.Transport.UDP, 1)) {
            runner.withPropertyValues("simple-secret.gb28181-zlm.enabled=true")
                    .withBean(GbZlmPlayer.class, () -> custom)
                    .run(context -> assertThat(context).hasNotFailed().getBean(GbZlmPlayer.class).isSameAs(custom));
        }
    }

    private ApplicationContextRunner dependencies() {
        return runner.withBean(Gb28181Server.class, () -> mock(Gb28181Server.class))
                .withBean(IZlmMediaService.class, () -> mock(IZlmMediaService.class));
    }
}

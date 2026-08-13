package com.ss.application.pushstream.config;

import com.ss.application.pushstream.process.ManagedStreamProcesses;
import com.ss.application.pushstream.service.MediaServerClient;
import com.ss.application.pushstream.service.PublishStreamScheduler;
import com.ss.application.pushstream.service.PublishStreamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 推流自动配置测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
class PublishStreamConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PublishStreamConfiguration.class));

    @Test
    void shouldNotCreateRuntimeBeansWhenDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PublishStreamProperties.class);
            assertThat(context).doesNotHaveBean("publishStreamScheduler");
            assertThat(context).doesNotHaveBean("ffmpegProcessManager");
        });
    }

    @Test
    void shouldCreateRuntimeBeansWhenEnabledWithRequiredCollaborator() {
        contextRunner.withBean(MediaServerClient.class, () -> app -> Set.of())
                .withPropertyValues(
                        "simple-secret.publish-stream.enabled=true",
                        "simple-secret.publish-stream.scan-directory=" + temporaryDirectory)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ManagedStreamProcesses.class);
                    assertThat(context).hasSingleBean(PublishStreamService.class);
                    assertThat(context).hasSingleBean(PublishStreamScheduler.class);
                });
    }
}

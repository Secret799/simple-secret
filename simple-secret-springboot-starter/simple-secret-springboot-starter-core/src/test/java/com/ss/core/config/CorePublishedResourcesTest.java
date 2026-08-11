package com.ss.core.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 core starter 发布的自动配置 SPI 与配置元数据。 */
class CorePublishedResourcesTest {

    @Test
    void shouldPublishAllAutoConfigurationsAndConfigurationMetadata() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream imports = classLoader.getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
             InputStream metadata = classLoader.getResourceAsStream(
                     "META-INF/spring-configuration-metadata.json")) {
            assertThat(imports).isNotNull();
            assertThat(read(imports))
                    .contains(SimpleSecretCoreAutoConfiguration.class.getName())
                    .contains(CoreExecutorAutoConfiguration.class.getName())
                    .contains(CoreAsyncAutoConfiguration.class.getName())
                    .contains(CoreValidationAutoConfiguration.class.getName());
            assertThat(metadata).isNotNull();
            assertThat(read(metadata))
                    .contains("simple-secret.core.project.name")
                    .contains("simple-secret.core.task-executor.enabled")
                    .contains("simple-secret.core.scheduler.enabled")
                    .contains("simple-secret.core.async.enabled")
                    .contains("simple-secret.core.validation.fail-fast");
        }
    }

    private static String read(InputStream input) throws IOException {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}

package com.ss.application.pushstream.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 推流配置元数据测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
class ConfigurationMetadataTest {

    @Test
    void shouldGeneratePublishStreamConfigurationMetadata() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring-configuration-metadata.json")) {
            assertThat(input).isNotNull();
            String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(metadata).contains(
                    "simple-secret.publish-stream.enabled",
                    "simple-secret.publish-stream.max-scanned-files",
                    "simple-secret.publish-stream.scan-directory");
        }
    }
}

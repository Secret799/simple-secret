package com.ss.application.djisei.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DJI SEI 配置元数据测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class ConfigurationMetadataTest {

    @Test
    void shouldPublishPayloadAndSummaryConfigurationMetadata() throws IOException {
        String resourceName = "META-INF/spring-configuration-metadata.json";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(input).as("DJI SEI application configuration metadata").isNotNull();
            String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(metadata).contains("simple-secret.dji-sei.max-payload-bytes");
            assertThat(metadata).contains("simple-secret.dji-sei.max-sei-nal-units");
            assertThat(metadata).contains("simple-secret.dji-sei.max-sei-messages");
            assertThat(metadata).contains("simple-secret.dji-sei.max-parse-issues");
            assertThat(metadata).contains("simple-secret.dji-sei.max-message-logs");
            assertThat(metadata).contains("simple-secret.dji-sei.summary-interval");
        }
    }
}

package com.ss.influxdb.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InfluxdbPublishedResourcesTest {
    @Test
    void shouldPublishAutoConfigurationImportAndConfigurationMetadata() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream imports = classLoader.getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
             InputStream metadata = classLoader.getResourceAsStream(
                     "META-INF/spring-configuration-metadata.json")) {
            assertThat(imports).isNotNull();
            assertThat(new String(imports.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("com.ss.influxdb.config.SimpleSecretInfluxdbAutoConfiguration");
            assertThat(metadata).isNotNull();
            String content = new String(metadata.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains("simple-secret.influxdb.enabled")
                    .contains("simple-secret.influxdb.batch-write.enabled")
                    .contains("simple-secret.influxdb.retention-policy.auto-create");
        }
    }
}

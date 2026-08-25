package com.ss.ics.dahua.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DahuaConfigurationMetadataTest {

    @Test
    void publishesConfigurationMetadataAndAutoConfigurationImport() throws IOException {
        try (InputStream metadata = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring-configuration-metadata.json")) {
            assertThat(metadata).isNotNull();
            String content = new String(metadata.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains(
                    "simple-secret.camera-sdk.dahua.enabled",
                    "simple-secret.camera-sdk.dahua.library-directory",
                    "simple-secret.camera-sdk.dahua.operation-timeout",
                    "simple-secret.camera-sdk.dahua.radiometry-search-timeout",
                    "simple-secret.camera-sdk.dahua.async-ptz-queue-capacity",
                    "simple-secret.camera-sdk.dahua.max-radiometry-results");
        }

        try (InputStream imports = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(imports).isNotNull();
            assertThat(new String(imports.readAllBytes(), StandardCharsets.UTF_8))
                    .contains(DahuaCameraSdkAutoConfiguration.class.getName());
        }
    }
}

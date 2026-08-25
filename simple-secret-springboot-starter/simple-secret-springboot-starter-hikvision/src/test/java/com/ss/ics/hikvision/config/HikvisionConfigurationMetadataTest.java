package com.ss.ics.hikvision.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HikvisionConfigurationMetadataTest {

    @Test
    void publishesConfigurationMetadataAndAutoConfigurationImport() throws IOException {
        try (InputStream metadata = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring-configuration-metadata.json")) {
            assertThat(metadata).isNotNull();
            String content = new String(metadata.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains(
                    "simple-secret.camera-sdk.hikvision.enabled",
                    "simple-secret.camera-sdk.hikvision.library-directory",
                    "simple-secret.camera-sdk.hikvision.file-search-timeout",
                    "simple-secret.camera-sdk.hikvision.async-ptz-queue-capacity");
        }

        try (InputStream imports = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(imports).isNotNull();
            assertThat(new String(imports.readAllBytes(), StandardCharsets.UTF_8))
                    .contains(HikvisionCameraSdkAutoConfiguration.class.getName());
        }
    }
}

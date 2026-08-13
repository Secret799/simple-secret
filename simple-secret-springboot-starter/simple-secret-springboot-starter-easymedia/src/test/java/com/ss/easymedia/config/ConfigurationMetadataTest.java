package com.ss.easymedia.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationMetadataTest {

    @Test
    void shouldGenerateEasyMediaConfigurationMetadata() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring-configuration-metadata.json")) {
            assertNotNull(input, "EasyMedia starter should generate Spring Boot configuration metadata");
            String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("simple-secret.easymedia.enabled"));
            assertTrue(metadata.contains("simple-secret.easymedia.max-track-frame-bytes"));
            assertTrue(metadata.contains("simple-secret.easymedia.webrtc.rate-limit.local-max-keys"));
        }
    }
}

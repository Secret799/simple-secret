package com.ss.redis.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationMetadataTest {

    @Test
    void generatesRedisConfigurationMetadata() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("META-INF/spring-configuration-metadata.json")) {
            assertThat(input).as("Redis starter configuration metadata").isNotNull();
            String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(metadata)
                    .contains("simple-secret.redis.enabled")
                    .contains("simple-secret.redis.mode")
                    .contains("simple-secret.redis.single.address")
                    .contains("simple-secret.redis.cluster.node-addresses")
                    .contains("simple-secret.redis.cache.enabled")
                    .contains("simple-secret.redis.cache.caches");
        }
    }
}

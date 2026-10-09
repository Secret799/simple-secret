package com.ss.gb28181.autoconfigure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;

class Gb28181PublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationImport() throws IOException {
        String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("com.ss.gb28181.autoconfigure.SimpleSecretGb28181AutoConfiguration");
        }
    }

    @Test
    void shouldGenerateCompleteConfigurationMetadata() throws IOException {
        Enumeration<java.net.URL> resources = getClass().getClassLoader()
                .getResources("META-INF/spring-configuration-metadata.json");
        boolean found = false;
        while (resources.hasMoreElements()) {
            try (InputStream input = resources.nextElement().openStream()) {
                String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                if (metadata.contains("simple-secret.gb28181.enabled")) {
                    assertThat(metadata)
                            .contains("simple-secret.gb28181.server-id")
                            .contains("simple-secret.gb28181.realm")
                            .contains("simple-secret.gb28181.bind-address")
                            .contains("simple-secret.gb28181.advertised-address")
                            .contains("simple-secret.gb28181.port")
                            .contains("simple-secret.gb28181.registration-ttl")
                            .contains("simple-secret.gb28181.heartbeat-interval")
                            .contains("simple-secret.gb28181.heartbeat-misses")
                            .contains("simple-secret.gb28181.query-timeout")
                            .contains("simple-secret.gb28181.invite-timeout")
                            .contains("simple-secret.gb28181.stop-timeout")
                            .contains("simple-secret.gb28181.max-play-sessions")
                            .contains("simple-secret.gb28181.max-devices")
                            .contains("simple-secret.gb28181.max-pending-queries")
                            .contains("simple-secret.gb28181.max-alarm-subscriptions")
                            .contains("simple-secret.gb28181.max-catalog-subscriptions")
                            .contains("simple-secret.gb28181.max-pending-catalog-notifications")
                            .contains("simple-secret.gb28181.max-mobile-position-subscriptions")
                            .contains("simple-secret.gb28181.max-pending-mobile-position-notifications")
                            .contains("simple-secret.gb28181.max-mobile-position-items")
                            .contains("simple-secret.gb28181.max-catalog-items")
                            .contains("simple-secret.gb28181.max-record-items")
                            .contains("simple-secret.gb28181.max-message-bytes");
                    assertThat(metadata).containsPattern("(?s)\\\"name\\\"\\s*:\\s*\\\"simple-secret\\.gb28181\\.max-record-items\\\"[^}]*\\\"defaultValue\\\"\\s*:\\s*10000");
                    assertThat(metadata).containsPattern("(?s)\\\"name\\\"\\s*:\\s*\\\"simple-secret\\.gb28181\\.max-alarm-subscriptions\\\"[^}]*\\\"defaultValue\\\"\\s*:\\s*256");
                    assertThat(metadata).containsPattern("(?s)\\\"name\\\"\\s*:\\s*\\\"simple-secret\\.gb28181\\.max-catalog-subscriptions\\\"[^}]*\\\"defaultValue\\\"\\s*:\\s*256");
                    assertThat(metadata).containsPattern("(?s)\\\"name\\\"\\s*:\\s*\\\"simple-secret\\.gb28181\\.max-pending-catalog-notifications\\\"[^}]*\\\"defaultValue\\\"\\s*:\\s*256");
                    assertThat(metadata).containsPattern("(?s)\\\"name\\\"\\s*:\\s*\\\"simple-secret\\.gb28181\\.max-mobile-position-subscriptions\\\"[^}]*\\\"defaultValue\\\"\\s*:\\s*256");
                    assertThat(metadata).containsPattern("(?s)\\\"name\\\"\\s*:\\s*\\\"simple-secret\\.gb28181\\.max-pending-mobile-position-notifications\\\"[^}]*\\\"defaultValue\\\"\\s*:\\s*256");
                    assertThat(metadata).containsPattern("(?s)\\\"name\\\"\\s*:\\s*\\\"simple-secret\\.gb28181\\.max-mobile-position-items\\\"[^}]*\\\"defaultValue\\\"\\s*:\\s*1000");
                    found = true;
                    break;
                }
            }
        }
        assertThat(found).as("GB28181 starter configuration metadata").isTrue();
    }
}

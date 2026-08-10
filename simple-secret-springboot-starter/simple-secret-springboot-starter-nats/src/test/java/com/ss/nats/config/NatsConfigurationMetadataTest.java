package com.ss.nats.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.assertj.core.api.Assertions.assertThat;

class NatsConfigurationMetadataTest {

    @Test
    void shouldGenerateNatsConfigurationMetadata() throws IOException {
        String resource = "META-INF/spring-configuration-metadata.json";
        Enumeration<java.net.URL> resources = getClass().getClassLoader().getResources(resource);
        boolean found = false;
        while (resources.hasMoreElements()) {
            try (InputStream input = resources.nextElement().openStream()) {
                String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                if (metadata.contains("simple-secret.nats.enabled")) {
                    assertThat(metadata)
                            .contains("simple-secret.nats.handler-core-size")
                            .contains("simple-secret.nats.clients");
                    found = true;
                    break;
                }
            }
        }
        assertThat(found).as("NATS starter configuration metadata").isTrue();
    }
}

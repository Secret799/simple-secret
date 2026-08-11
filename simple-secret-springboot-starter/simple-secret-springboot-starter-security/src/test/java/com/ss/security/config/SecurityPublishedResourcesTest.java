package com.ss.security.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Security starter 发布的自动配置入口和配置元数据。 */
class SecurityPublishedResourcesTest {
    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    private static final String CONFIGURATION_METADATA =
            "META-INF/spring-configuration-metadata.json";

    @Test
    void shouldPublishExactAutoConfigurationImportAndMetadataDefaults() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream imports = classLoader.getResourceAsStream(AUTO_CONFIGURATION_IMPORTS);
             InputStream metadata = classLoader.getResourceAsStream(CONFIGURATION_METADATA)) {
            assertThat(imports).isNotNull();
            assertThat(read(imports).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList())
                    .containsExactly(SimpleSecretSecurityAutoConfiguration.class.getName());

            assertThat(metadata).isNotNull();
            JsonNode root = new ObjectMapper().readTree(read(metadata));
            assertThat(property(root, "simple-secret.security.enabled")
                    .path("defaultValue").asBoolean()).isFalse();
            assertThat(property(root, "simple-secret.security.path-patterns")
                    .path("defaultValue")).isEqualTo(new ObjectMapper().readTree("[\"/**\"]"));
            assertThat(property(root, "simple-secret.security.exclude-path-patterns")
                    .path("defaultValue")).isEqualTo(new ObjectMapper().readTree("[]"));
            assertThat(property(root, "simple-secret.security.order")
                    .path("defaultValue").asInt()).isZero();
        }
    }

    private static JsonNode property(JsonNode metadata, String name) {
        return StreamSupport.stream(metadata.path("properties").spliterator(), false)
                .filter(candidate -> name.equals(candidate.path("name").asText()))
                .findFirst()
                .orElseThrow();
    }

    private static String read(InputStream input) throws Exception {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}

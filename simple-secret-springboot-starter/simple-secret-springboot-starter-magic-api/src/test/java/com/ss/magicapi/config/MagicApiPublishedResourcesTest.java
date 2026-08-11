package com.ss.magicapi.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 starter 发布自动配置 SPI 和配置元数据。 */
class MagicApiPublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationFilterDefaultsProcessorAndMetadata() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream imports = classLoader.getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
             InputStream factories = classLoader.getResourceAsStream("META-INF/spring.factories");
             InputStream metadata = classLoader.getResourceAsStream(
                     "META-INF/spring-configuration-metadata.json")) {
            assertThat(imports).isNotNull();
            assertThat(read(imports)).contains(SimpleSecretMagicApiAutoConfiguration.class.getName());
            assertThat(factories).isNotNull();
            assertThat(read(factories))
                    .contains(MagicApiAutoConfigurationImportFilter.class.getName())
                    .contains(MagicApiEnvironmentPostProcessor.class.getName());
            assertThat(metadata).isNotNull();
            assertThat(read(metadata)).contains("simple-secret.magic-api.enabled");
        }
    }

    private static String read(InputStream input) throws IOException {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}

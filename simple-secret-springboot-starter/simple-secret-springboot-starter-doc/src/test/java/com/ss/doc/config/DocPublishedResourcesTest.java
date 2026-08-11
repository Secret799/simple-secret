package com.ss.doc.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 doc starter 发布自动配置 SPI、配置元数据和使用教程。 */
class DocPublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationDefaultsMetadataAndReadme() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream imports = classLoader.getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
             InputStream factories = classLoader.getResourceAsStream("META-INF/spring.factories");
             InputStream metadata = classLoader.getResourceAsStream(
                     "META-INF/spring-configuration-metadata.json")) {
            assertThat(imports).isNotNull();
            assertThat(read(imports)).contains(SimpleSecretDocAutoConfiguration.class.getName());
            assertThat(factories).isNotNull();
            assertThat(read(factories)).contains(DocEnvironmentPostProcessor.class.getName());
            assertThat(metadata).isNotNull();
            assertThat(read(metadata))
                    .contains("simple-secret.doc.enabled")
                    .contains("simple-secret.doc.security.schemes")
                    .contains("simple-secret.doc.javadoc-tags-enabled");
        }

        String readme = Files.readString(Path.of("README.md"));
        assertThat(readme)
                .contains("spring-boot-starter-web")
                .contains("simple-secret.doc.enabled")
                .contains("API_KEY")
                .contains("HTTP_BASIC")
                .contains("HTTP_BEARER")
                .contains("Swagger UI")
                .contains("Knife4j")
                .contains("Therapi")
                .contains("生产环境");
    }

    private static String read(InputStream input) throws Exception {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}

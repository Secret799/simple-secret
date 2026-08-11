package com.ss.web.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Web starter 发布自动配置 SPI、配置元数据和使用教程。 */
class WebPublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationMetadataAndReadme() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream imports = classLoader.getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
             InputStream metadata = classLoader.getResourceAsStream(
                     "META-INF/spring-configuration-metadata.json")) {
            assertThat(imports).isNotNull();
            assertThat(read(imports)).contains(SimpleSecretWebAutoConfiguration.class.getName());
            assertThat(metadata).isNotNull();
            assertThat(read(metadata))
                    .contains("simple-secret.web.enabled")
                    .contains("simple-secret.web.exception-handler.enabled")
                    .contains("simple-secret.web.cors.enabled")
                    .contains("simple-secret.web.cors.max-age")
                    .contains("simple-secret.web.request-timing.enabled");
        }

        String readme = Files.readString(Path.of("README.md"));
        assertThat(readme)
                .contains("spring-boot-starter-web")
                .contains("simple-secret.web.enabled")
                .contains("BaseController")
                .contains("https://app.example.com")
                .contains("HandlerMappingIntrospector")
                .contains("输出编码")
                .contains("模板转义")
                .contains("CSP")
                .contains("输入校验")
                .contains("不迁移");
    }

    private static String read(InputStream input) throws Exception {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}

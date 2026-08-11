package com.ss.sensitive.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 sensitive 自动配置资源和使用文档进入发布模块。 */
class SensitivePublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationImportsMetadataAndReadme() throws Exception {
        Path imports = Path.of("src/main/resources/META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        Path metadata = Path.of(
                "src/main/resources/META-INF/spring-configuration-metadata.json");

        assertThat(Files.readString(imports).trim())
                .isEqualTo(SimpleSecretSensitiveAutoConfiguration.class.getName());
        assertThat(Files.readString(metadata))
                .contains("simple-secret.sensitive.enabled");
        assertThat(Files.readString(Path.of("README.md")))
                .contains("@Sensitive")
                .contains("SensitiveService")
                .contains("SimpleSecretSensitiveModule")
                .contains("默认脱敏");
    }
}

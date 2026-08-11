package com.ss.tenant.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证自动配置、配置元数据和使用文档进入发布模块。 */
class TenantPublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationImportsMetadataAndReadme() throws Exception {
        Path imports = Path.of("src/main/resources/META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        Path metadata = Path.of(
                "src/main/resources/META-INF/spring-configuration-metadata.json");

        assertThat(Files.readString(imports).trim())
                .isEqualTo(SimpleSecretTenantAutoConfiguration.class.getName());
        assertThat(Files.readString(metadata))
                .contains("simple-secret.tenant.enabled")
                .contains("simple-secret.tenant.column")
                .contains("simple-secret.tenant.excluded-tables");
        assertThat(Files.readString(Path.of("README.md")))
                .contains("TenantContextProvider")
                .contains("TenantContext")
                .contains("TenantEntity")
                .contains("fail closed");
    }
}

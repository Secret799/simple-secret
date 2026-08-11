package com.ss.mybatis.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证自动配置导入和 IDE 配置元数据进入发布资源。 */
class MybatisPublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationImportsAndMetadata() throws Exception {
        Path imports = Path.of("src/main/resources/META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        Path metadata = Path.of(
                "src/main/resources/META-INF/spring-configuration-metadata.json");

        assertThat(Files.readString(imports).trim())
                .isEqualTo(SimpleSecretMybatisAutoConfiguration.class.getName());
        String metadataText = Files.readString(metadata);
        assertThat(metadataText)
                .contains("simple-secret.mybatis.enabled")
                .contains("simple-secret.mybatis.pagination-enabled")
                .contains("simple-secret.mybatis.optimistic-locker-enabled")
                .contains("simple-secret.mybatis.max-page-size")
                .contains("simple-secret.mybatis.overflow");
    }
}

package com.ss.encrypt.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptPublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationAndMetadata() throws Exception {
        String imports = Files.readString(Path.of(
                "src/main/resources/META-INF/spring/"
                        + "org.springframework.boot.autoconfigure.AutoConfiguration.imports"));
        String metadata = Files.readString(Path.of(
                "src/main/resources/META-INF/spring-configuration-metadata.json"));

        assertThat(imports).contains(
                "com.ss.encrypt.config.SimpleSecretEncryptAutoConfiguration");
        assertThat(metadata).contains(
                "simple-secret.encrypt.enabled",
                "simple-secret.encrypt.api.enabled",
                "simple-secret.encrypt.mybatis.enabled");
    }
}

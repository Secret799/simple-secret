package com.ss.websocket.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证自动配置发现文件和配置元数据。 */
class WebSocketPublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationImportAndMetadata() throws Exception {
        Path imports = Path.of("src/main/resources/META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        Path metadata = Path.of("src/main/resources/META-INF/spring-configuration-metadata.json");

        assertThat(Files.readString(imports).trim())
                .isEqualTo("com.ss.websocket.config.SimpleSecretWebSocketAutoConfiguration");
        assertThat(Files.readString(metadata))
                .contains("simple-secret.websocket.enabled")
                .contains("simple-secret.websocket.paths")
                .contains("simple-secret.websocket.allowed-origins")
                .contains("simple-secret.websocket.send-time-limit")
                .contains("simple-secret.websocket.send-buffer-size")
                .contains("simple-secret.websocket.node-id");
    }
}

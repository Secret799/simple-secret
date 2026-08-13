package com.ss.netty.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NettyWebSocketPublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationAndConfigurationMetadata() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        String imports = read(loader, "META-INF/spring/"
                + "org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        String metadata = read(loader, "META-INF/spring-configuration-metadata.json");

        assertThat(imports.trim()).isEqualTo(
                "com.ss.netty.config.SimpleSecretNettyWebSocketAutoConfiguration");
        assertThat(metadata)
                .contains("simple-secret.netty.websocket.enabled")
                .contains("simple-secret.netty.websocket.auto-startup")
                .contains("simple-secret.netty.websocket.host")
                .contains("simple-secret.netty.websocket.port")
                .contains("simple-secret.netty.websocket.endpoints")
                .contains("simple-secret.netty.websocket.max-http-content-length")
                .contains("simple-secret.netty.websocket.max-frame-payload-length")
                .contains("simple-secret.netty.websocket.handshake-timeout")
                .contains("simple-secret.netty.websocket.shutdown-timeout")
                .contains("simple-secret.netty.websocket.handler-queue-capacity");
    }

    private static String read(ClassLoader loader, String name) throws Exception {
        try (InputStream stream = loader.getResourceAsStream(name)) {
            assertThat(stream).as(name).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

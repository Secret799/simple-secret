package com.ss.idempotent.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证自动配置声明和配置元数据被发布。 */
class IdempotentPublishedResourcesTest {

    @Test
    void shouldPublishAutoConfigurationAndMetadata() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertThat(read(loader,
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"))
                .containsExactly("com.ss.idempotent.config.SimpleSecretIdempotentAutoConfiguration");

        String metadata = readText(loader,
                "META-INF/spring-configuration-metadata.json");
        assertThat(metadata)
                .contains("simple-secret.idempotent.enabled")
                .contains("simple-secret.idempotent.key-prefix")
                .contains("simple-secret.idempotent.identity-header");
    }

    private static String[] read(ClassLoader loader, String name) throws Exception {
        return readText(loader, name).lines()
                .filter(line -> !line.isBlank())
                .toArray(String[]::new);
    }

    private static String readText(ClassLoader loader, String name) throws Exception {
        try (InputStream input = loader.getResourceAsStream(name)) {
            assertThat(input).as(name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

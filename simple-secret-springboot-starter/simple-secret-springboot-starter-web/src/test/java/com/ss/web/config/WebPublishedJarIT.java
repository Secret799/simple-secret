package com.ss.web.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 package 阶段生成的发布 JAR 包含 Web 自动配置资源。 */
class WebPublishedJarIT {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    private static final String CONFIGURATION_METADATA =
            "META-INF/spring-configuration-metadata.json";

    @Test
    void shouldPublishWebResourcesInMainJar() throws Exception {
        String artifactId = property("simple-secret.web.published-artifact-id");
        String version = property("simple-secret.web.published-version");
        String finalName = property("simple-secret.web.published-final-name");
        Path jarPath = Path.of(
                property("simple-secret.web.published-build-directory"), finalName + ".jar");

        assertThat(finalName).isEqualTo(artifactId + "-" + version);
        assertThat(Files.isRegularFile(jarPath)).isTrue();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            assertThat(readEntry(jar, AUTO_CONFIGURATION_IMPORTS))
                    .contains(SimpleSecretWebAutoConfiguration.class.getName());
            assertThat(readEntry(jar, CONFIGURATION_METADATA))
                    .contains("simple-secret.web.enabled")
                    .contains("simple-secret.web.exception-handler.enabled")
                    .contains("simple-secret.web.cors.enabled")
                    .contains("simple-secret.web.cors.path")
                    .contains("simple-secret.web.cors.allowed-origins")
                    .contains("simple-secret.web.cors.allowed-origin-patterns")
                    .contains("simple-secret.web.cors.allowed-methods")
                    .contains("simple-secret.web.cors.allowed-headers")
                    .contains("simple-secret.web.cors.exposed-headers")
                    .contains("simple-secret.web.cors.allow-credentials")
                    .contains("simple-secret.web.cors.max-age")
                    .contains("simple-secret.web.request-timing.enabled")
                    .contains("simple-secret.web.request-timing.slow-request-threshold");
        }
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        assertThat(value).as(name).isNotBlank();
        return value;
    }

    private static String readEntry(JarFile jar, String name) throws Exception {
        JarEntry entry = jar.getJarEntry(name);
        assertThat(entry).as(name).isNotNull();
        try (InputStream input = jar.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

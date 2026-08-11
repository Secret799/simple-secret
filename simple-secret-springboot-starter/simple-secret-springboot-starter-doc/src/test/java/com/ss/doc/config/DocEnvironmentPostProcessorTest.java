package com.ss.doc.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 doc starter 的文档暴露安全默认值。 */
class DocEnvironmentPostProcessorTest {

    @Test
    void shouldDisableApiDocsByDefault() {
        StandardEnvironment environment = new StandardEnvironment();

        new DocEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication());

        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
    }

    @Test
    void shouldEnableApiDocsWhenSimpleSecretDocIsEnabled() {
        StandardEnvironment environment = environmentWith(Map.of(
                "simple-secret.doc.enabled", "true"));

        new DocEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication());

        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isTrue();
    }

    @Test
    void shouldKeepExplicitSpringdocSettingAndRegisterDefaultsOnce() {
        StandardEnvironment environment = environmentWith(Map.of(
                "simple-secret.doc.enabled", "true",
                "springdoc.api-docs.enabled", "false"));
        DocEnvironmentPostProcessor processor = new DocEnvironmentPostProcessor();

        processor.postProcessEnvironment(environment, new SpringApplication());
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        long defaultsCount = StreamSupport.stream(
                        environment.getPropertySources().spliterator(), false)
                .filter(source -> DocEnvironmentPostProcessor.PROPERTY_SOURCE_NAME.equals(source.getName()))
                .count();
        assertThat(defaultsCount).isOne();
    }

    private static StandardEnvironment environmentWith(Map<String, Object> values) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", values));
        return environment;
    }
}

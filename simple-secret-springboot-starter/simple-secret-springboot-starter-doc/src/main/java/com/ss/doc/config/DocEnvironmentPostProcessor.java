package com.ss.doc.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/** 为 springdoc API 文档提供可被应用配置覆盖的安全默认值。 */
public final class DocEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "simpleSecretDocDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        boolean enabled = environment.getProperty(
                "simple-secret.doc.enabled", Boolean.class, false);
        environment.getPropertySources().addLast(new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                Map.of("springdoc.api-docs.enabled", enabled)));
    }
}

package com.ss.magicapi.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * 为 Magic API 提供可被应用配置覆盖的安全默认值。
 */
public final class MagicApiEnvironmentPostProcessor implements EnvironmentPostProcessor {
    static final String PROPERTY_SOURCE_NAME = "simpleSecretMagicApiDefaults";

    private static final Map<String, Object> DEFAULTS = Map.of(
            "magic-api.banner", false,
            "magic-api.support-cross-domain", false,
            "magic-api.show-sql", false,
            "magic-api.show-url", false,
            "magic-api.resource.readonly", true,
            "magic-api.page.max-page-size", 1000L);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        if (!environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addLast(
                    new MapPropertySource(PROPERTY_SOURCE_NAME, DEFAULTS));
        }
    }
}

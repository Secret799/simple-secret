package com.ss.magicapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Magic API 安全默认值及其覆盖优先级。 */
class MagicApiEnvironmentPostProcessorTest {

    @Test
    void shouldApplySafeDefaults() {
        StandardEnvironment environment = new StandardEnvironment();

        new MagicApiEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication());

        assertThat(environment.getProperty("magic-api.banner", Boolean.class)).isFalse();
        assertThat(environment.getProperty("magic-api.support-cross-domain", Boolean.class)).isFalse();
        assertThat(environment.getProperty("magic-api.show-sql", Boolean.class)).isFalse();
        assertThat(environment.getProperty("magic-api.show-url", Boolean.class)).isFalse();
        assertThat(environment.getProperty("magic-api.resource.readonly", Boolean.class)).isTrue();
        assertThat(environment.getProperty("magic-api.page.max-page-size", Long.class)).isEqualTo(1000L);
    }

    @Test
    void shouldAllowApplicationPropertiesToOverrideDefaults() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
                "magic-api.show-sql", "true",
                "magic-api.resource.readonly", "false",
                "magic-api.page.max-page-size", "200")));

        new MagicApiEnvironmentPostProcessor().postProcessEnvironment(
                environment, new SpringApplication());

        assertThat(environment.getProperty("magic-api.show-sql", Boolean.class)).isTrue();
        assertThat(environment.getProperty("magic-api.resource.readonly", Boolean.class)).isFalse();
        assertThat(environment.getProperty("magic-api.page.max-page-size", Long.class)).isEqualTo(200L);
    }
}

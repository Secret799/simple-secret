package com.ss.magicapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Magic API 上游自动配置的显式启用边界。 */
class MagicApiAutoConfigurationImportFilterTest {
    private static final String MAGIC_API_AUTO_CONFIGURATION =
            "org.ssssssss.magicapi.spring.boot.starter.MagicAPIAutoConfiguration";

    @Test
    void shouldRejectMagicApiAutoConfigurationByDefault() {
        assertThat(matches(new StandardEnvironment(), MAGIC_API_AUTO_CONFIGURATION)).isFalse();
    }

    @Test
    void shouldAcceptMagicApiAutoConfigurationOnlyWhenExplicitlyEnabled() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test", Map.of("simple-secret.magic-api.enabled", "true")));

        assertThat(matches(environment, MAGIC_API_AUTO_CONFIGURATION)).isTrue();
    }

    @Test
    void shouldRejectValuesOtherThanLiteralTrue() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "test", Map.of("simple-secret.magic-api.enabled", "yes")));

        assertThat(matches(environment, MAGIC_API_AUTO_CONFIGURATION)).isFalse();
    }

    @Test
    void shouldNeverFilterUnrelatedAutoConfiguration() {
        assertThat(matches(new StandardEnvironment(), "example.OtherAutoConfiguration")).isTrue();
    }

    private static boolean matches(StandardEnvironment environment, String autoConfiguration) {
        MagicApiAutoConfigurationImportFilter filter = new MagicApiAutoConfigurationImportFilter();
        filter.setEnvironment(environment);
        return filter.match(new String[]{autoConfiguration}, null)[0];
    }
}

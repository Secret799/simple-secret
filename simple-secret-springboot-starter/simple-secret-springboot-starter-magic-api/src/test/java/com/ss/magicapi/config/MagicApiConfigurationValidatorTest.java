package com.ss.magicapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Magic API 启用前的资源与编辑器安全配置。 */
class MagicApiConfigurationValidatorTest {

    @Test
    void shouldRequireExplicitFileLocation() {
        assertThatThrownBy(() -> validate(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("magic-api.resource.location");
    }

    @Test
    void shouldRejectUnsupportedResourceType() {
        assertThatThrownBy(() -> validate(Map.of("magic-api.resource.type", "redis")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("magic-api.resource.type")
                .hasMessageContaining("file")
                .hasMessageContaining("database");
    }

    @Test
    void shouldRejectPartialEditorCredentialsEvenWhenEditorIsDisabled() {
        assertThatThrownBy(() -> validate(Map.of(
                "magic-api.resource.location", "./magic-api",
                "magic-api.security.username", "admin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username")
                .hasMessageContaining("password");
    }

    @Test
    void shouldRequireCredentialsWhenEditorIsEnabled() {
        assertThatThrownBy(() -> validate(Map.of(
                "magic-api.resource.location", "./magic-api",
                "magic-api.web", "/magic/web")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("magic-api.web")
                .hasMessageContaining("username")
                .hasMessageContaining("password");
    }

    @Test
    void shouldAcceptExplicitFileResourceWithoutEditor() {
        assertThatCode(() -> validate(Map.of(
                "magic-api.resource.type", "file",
                "magic-api.resource.location", "./magic-api")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptAuthenticatedEditor() {
        assertThatCode(() -> validate(Map.of(
                "magic-api.resource.location", "./magic-api",
                "magic-api.web", "/magic/web",
                "magic-api.security.username", "admin",
                "magic-api.security.password", "secret")))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptDatabaseResourceWithoutFileLocation() {
        assertThatCode(() -> validate(Map.of("magic-api.resource.type", "database")))
                .doesNotThrowAnyException();
    }

    private static void validate(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        MagicApiConfigurationValidator.validate(environment);
    }
}

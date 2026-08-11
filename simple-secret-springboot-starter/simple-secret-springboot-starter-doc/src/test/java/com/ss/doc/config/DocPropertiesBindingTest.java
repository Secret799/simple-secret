package com.ss.doc.config;

import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 doc starter 公共配置模型及默认值。 */
class DocPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void shouldUseDisabledAndEmptyDefaults() {
        runner.run(context -> {
            DocProperties properties = context.getBean(DocProperties.class);

            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.isJavadocTagsEnabled()).isFalse();
            assertThat(properties.getInfo()).isNotNull();
            assertThat(properties.getSecurity().getSchemes()).isEmpty();
            assertThat(properties.getSecurity().getGloballyRequired()).isEmpty();
        });
    }

    @Test
    void shouldBindInfoAndSecuritySchemes() {
        runner.withPropertyValues(
                        "simple-secret.doc.enabled=true",
                        "simple-secret.doc.javadoc-tags-enabled=true",
                        "simple-secret.doc.info.title=Orders API",
                        "simple-secret.doc.info.description=Order operations",
                        "simple-secret.doc.info.version=2.0",
                        "simple-secret.doc.info.terms-of-service=https://example.test/terms",
                        "simple-secret.doc.info.contact.name=API Team",
                        "simple-secret.doc.info.contact.email=api@example.test",
                        "simple-secret.doc.info.contact.url=https://example.test/team",
                        "simple-secret.doc.info.license.name=Apache-2.0",
                        "simple-secret.doc.info.license.url=https://www.apache.org/licenses/LICENSE-2.0",
                        "simple-secret.doc.security.schemes.apiKey.type=api-key",
                        "simple-secret.doc.security.schemes.apiKey.location=header",
                        "simple-secret.doc.security.schemes.apiKey.parameter-name=X-API-Key",
                        "simple-secret.doc.security.schemes.bearerAuth.type=http-bearer",
                        "simple-secret.doc.security.schemes.bearerAuth.bearer-format=JWT",
                        "simple-secret.doc.security.globally-required[0]=bearerAuth")
                .run(context -> {
                    DocProperties properties = context.getBean(DocProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.isJavadocTagsEnabled()).isTrue();
                    assertThat(properties.getInfo().getTitle()).isEqualTo("Orders API");
                    assertThat(properties.getInfo().getContact().getName()).isEqualTo("API Team");
                    assertThat(properties.getInfo().getLicense().getName()).isEqualTo("Apache-2.0");
                    assertThat(properties.getSecurity().getSchemes()).containsOnlyKeys("apiKey", "bearerAuth");
                    assertThat(properties.getSecurity().getSchemes().get("apiKey").getType())
                            .isEqualTo(DocProperties.SecurityType.API_KEY);
                    assertThat(properties.getSecurity().getSchemes().get("apiKey").getLocation())
                            .isEqualTo(SecurityScheme.In.HEADER);
                    assertThat(properties.getSecurity().getSchemes().get("bearerAuth").getType())
                            .isEqualTo(DocProperties.SecurityType.HTTP_BEARER);
                    assertThat(properties.getSecurity().getGloballyRequired())
                            .containsExactly("bearerAuth");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DocProperties.class)
    static class PropertiesConfiguration {
    }
}

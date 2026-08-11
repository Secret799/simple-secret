package com.ss.doc.config;

import com.ss.doc.customizer.JavadocTagOperationCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 doc starter 的 OpenAPI 自动配置和消费者覆盖边界。 */
class SimpleSecretDocAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretDocAutoConfiguration.class));

    @Test
    void shouldRemainDisabledByDefaultAndOutsideServletApplications() {
        webRunner.run(context -> assertThat(context).doesNotHaveBean(OpenAPI.class));

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleSecretDocAutoConfiguration.class))
                .withPropertyValues("simple-secret.doc.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(OpenAPI.class));
    }

    @Test
    void shouldCreateConfiguredInfoOnlyWhenEnabled() {
        webRunner.withPropertyValues(
                        "simple-secret.doc.enabled=true",
                        "simple-secret.doc.info.title=Orders API",
                        "simple-secret.doc.info.description=Order operations",
                        "simple-secret.doc.info.version=2.0",
                        "simple-secret.doc.info.terms-of-service=https://example.test/terms",
                        "simple-secret.doc.info.contact.name=API Team",
                        "simple-secret.doc.info.contact.email=api@example.test",
                        "simple-secret.doc.info.contact.url=https://example.test/team",
                        "simple-secret.doc.info.license.name=Apache-2.0",
                        "simple-secret.doc.info.license.url=https://www.apache.org/licenses/LICENSE-2.0")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAPI.class);
                    OpenAPI openApi = context.getBean(OpenAPI.class);
                    assertThat(openApi.getInfo().getTitle()).isEqualTo("Orders API");
                    assertThat(openApi.getInfo().getDescription()).isEqualTo("Order operations");
                    assertThat(openApi.getInfo().getVersion()).isEqualTo("2.0");
                    assertThat(openApi.getInfo().getTermsOfService())
                            .isEqualTo("https://example.test/terms");
                    assertThat(openApi.getInfo().getContact().getName()).isEqualTo("API Team");
                    assertThat(openApi.getInfo().getLicense().getName()).isEqualTo("Apache-2.0");
                    assertThat(openApi.getComponents()).isNull();
                    assertThat(openApi.getSecurity()).isNull();
                });
    }

    @Test
    void shouldCreateSupportedSecuritySchemesAndOnlyConfiguredGlobalRequirements() {
        webRunner.withPropertyValues(
                        "simple-secret.doc.enabled=true",
                        "simple-secret.doc.security.schemes.apiKey.type=api-key",
                        "simple-secret.doc.security.schemes.apiKey.location=query",
                        "simple-secret.doc.security.schemes.apiKey.parameter-name=access_token",
                        "simple-secret.doc.security.schemes.apiKey.description=Query token",
                        "simple-secret.doc.security.schemes.basicAuth.type=http-basic",
                        "simple-secret.doc.security.schemes.bearerAuth.type=http-bearer",
                        "simple-secret.doc.security.schemes.bearerAuth.bearer-format=JWT",
                        "simple-secret.doc.security.globally-required[0]=apiKey",
                        "simple-secret.doc.security.globally-required[1]=bearerAuth")
                .run(context -> {
                    OpenAPI openApi = context.getBean(OpenAPI.class);
                    SecurityScheme apiKey = openApi.getComponents().getSecuritySchemes().get("apiKey");
                    SecurityScheme basic = openApi.getComponents().getSecuritySchemes().get("basicAuth");
                    SecurityScheme bearer = openApi.getComponents().getSecuritySchemes().get("bearerAuth");

                    assertThat(apiKey.getType()).isEqualTo(SecurityScheme.Type.APIKEY);
                    assertThat(apiKey.getIn()).isEqualTo(SecurityScheme.In.QUERY);
                    assertThat(apiKey.getName()).isEqualTo("access_token");
                    assertThat(apiKey.getDescription()).isEqualTo("Query token");
                    assertThat(basic.getType()).isEqualTo(SecurityScheme.Type.HTTP);
                    assertThat(basic.getScheme()).isEqualTo("basic");
                    assertThat(bearer.getType()).isEqualTo(SecurityScheme.Type.HTTP);
                    assertThat(bearer.getScheme()).isEqualTo("bearer");
                    assertThat(bearer.getBearerFormat()).isEqualTo("JWT");
                    assertThat(openApi.getSecurity()).singleElement().satisfies(requirement ->
                            assertThat(requirement).containsOnlyKeys("apiKey", "bearerAuth"));
                });
    }

    @Test
    void shouldRejectUnknownGlobalSchemeAndBlankApiKeyParameter() {
        webRunner.withPropertyValues(
                        "simple-secret.doc.enabled=true",
                        "simple-secret.doc.security.globally-required[0]=missing")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining("simple-secret.doc.security.globally-required")
                        .hasMessageContaining("missing"));

        webRunner.withPropertyValues(
                        "simple-secret.doc.enabled=true",
                        "simple-secret.doc.security.schemes.apiKey.type=api-key",
                        "simple-secret.doc.security.schemes.apiKey.parameter-name= ")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause()
                        .hasMessageContaining("simple-secret.doc.security.schemes.apiKey.parameter-name"));
    }

    @Test
    void shouldBackOffWhenConsumerProvidesOpenApi() {
        webRunner.withUserConfiguration(ConsumerOpenApiConfiguration.class)
                .withPropertyValues("simple-secret.doc.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAPI.class);
                    assertThat(context.getBean(OpenAPI.class))
                            .isSameAs(ConsumerOpenApiConfiguration.OPEN_API);
                });
    }

    @Test
    void shouldPublishJavadocCustomizerOnlyWhenExplicitlyEnabled() {
        webRunner.withPropertyValues("simple-secret.doc.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean("simpleSecretJavadocTagOperationCustomizer"));

        webRunner.withPropertyValues(
                        "simple-secret.doc.enabled=true",
                        "simple-secret.doc.javadoc-tags-enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(JavadocTagOperationCustomizer.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerOpenApiConfiguration {
        private static final OpenAPI OPEN_API = new OpenAPI();

        @Bean
        OpenAPI consumerOpenApi() {
            return OPEN_API;
        }
    }
}

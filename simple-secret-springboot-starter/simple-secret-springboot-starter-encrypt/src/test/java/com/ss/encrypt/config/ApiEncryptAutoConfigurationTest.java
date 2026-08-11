package com.ss.encrypt.config;

import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.key.EncryptionKeyProvider;
import com.ss.encrypt.web.ApiEncryptionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ApiEncryptAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretEncryptAutoConfiguration.class,
                    ApiEncryptAutoConfiguration.class))
            .withUserConfiguration(ApiInfrastructureConfiguration.class);

    @Test
    void shouldRegisterFilterOnlyWhenBothSwitchesAndKeysAreValid() {
        runner.withPropertyValues("simple-secret.encrypt.enabled=true")
                .run(context ->
                        assertThat(context).doesNotHaveBean(ApiEncryptionFilter.class));

        runner.withPropertyValues(
                        "simple-secret.encrypt.enabled=true",
                        "simple-secret.encrypt.api.enabled=true",
                        "simple-secret.encrypt.api.request-key-id=request",
                        "simple-secret.encrypt.api.response-key-id=response")
                .run(context -> {
                    assertThat(context).hasSingleBean(ApiEncryptionFilter.class);
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                });
    }

    @Test
    void shouldFailClosedWhenRequiredKeyIdIsMissing() {
        runner.withPropertyValues(
                        "simple-secret.encrypt.enabled=true",
                        "simple-secret.encrypt.api.enabled=true",
                        "simple-secret.encrypt.api.response-key-id=response")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("request-key-id");
                });
    }

    @Test
    void shouldFailClosedWhenRsaKeyFormatIsInvalid() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SimpleSecretEncryptAutoConfiguration.class,
                        ApiEncryptAutoConfiguration.class))
                .withUserConfiguration(HandlerMappingConfiguration.class)
                .withPropertyValues(
                        "simple-secret.encrypt.enabled=true",
                        "simple-secret.encrypt.api.enabled=true",
                        "simple-secret.encrypt.api.request-key-id=request",
                        "simple-secret.encrypt.api.response-key-id=response",
                        "simple-secret.encrypt.keys.request.private-key=not-a-key",
                        "simple-secret.encrypt.keys.response.public-key=not-a-key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("RSA private key")
                            .hasMessageNotContaining("not-a-key");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ApiInfrastructureConfiguration {

        @Bean
        RequestMappingHandlerMapping requestMappingHandlerMapping() {
            return new RequestMappingHandlerMapping();
        }

        @Bean
        EncryptionKeyProvider consumerApiKeyProvider() throws Exception {
            EncryptionMaterial request = material();
            EncryptionMaterial response = material();
            return (keyId, algorithm) -> "request".equals(keyId) ? request : response;
        }

        private static EncryptionMaterial material() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return EncryptionMaterial.asymmetric(
                    Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HandlerMappingConfiguration {

        @Bean
        RequestMappingHandlerMapping requestMappingHandlerMapping() {
            return new RequestMappingHandlerMapping();
        }
    }
}

package com.ss.encrypt.config;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.core.EncryptionRequest;
import com.ss.encrypt.core.EncryptionService;
import com.ss.encrypt.key.EncryptionKeyProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleSecretEncryptAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretEncryptAutoConfiguration.class));

    @Test
    void shouldRemainDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(EncryptionService.class);
            assertThat(context).doesNotHaveBean(EncryptionKeyProvider.class);
        });
    }

    @Test
    void shouldCreateCoreServiceWhenExplicitlyEnabled() {
        runner.withPropertyValues(
                        "simple-secret.encrypt.enabled=true",
                        "simple-secret.encrypt.keys.primary.secret-key="
                                + "AAECAwQFBgcICQoLDA0ODw==")
                .run(context -> {
                    assertThat(context).hasSingleBean(EncryptionService.class);
                    assertThat(context).hasSingleBean(EncryptionKeyProvider.class);

                    EncryptionService service = context.getBean(EncryptionService.class);
                    EncryptionRequest request = new EncryptionRequest(
                            EncryptionAlgorithm.AES_GCM,
                            CipherEncoding.BASE64, "primary");
                    String encrypted = service.encrypt("configured", request);
                    assertThat(service.decrypt(encrypted, request))
                            .isEqualTo("configured");
                });
    }

    @Test
    void shouldBackOffForConsumerProviderAndService() {
        runner.withPropertyValues("simple-secret.encrypt.enabled=true")
                .withUserConfiguration(OverrideConfiguration.class)
                .run(context -> {
                    assertThat(context).getBean(EncryptionKeyProvider.class)
                            .isSameAs(context.getBean("consumerKeyProvider"));
                    assertThat(context).getBean(EncryptionService.class)
                            .isSameAs(context.getBean("consumerEncryptionService"));
                });
    }

    @Test
    void shouldFailWhenConfiguredKeyMixesSymmetricAndAsymmetricMaterial() {
        runner.withPropertyValues(
                        "simple-secret.encrypt.enabled=true",
                        "simple-secret.encrypt.keys.invalid.secret-key="
                                + "AAECAwQFBgcICQoLDA0ODw==",
                        "simple-secret.encrypt.keys.invalid.public-key=ZmFrZQ==")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("invalid")
                            .hasMessageNotContaining("AAECAwQ");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class OverrideConfiguration {

        @Bean
        EncryptionKeyProvider consumerKeyProvider() {
            return (keyId, algorithm) ->
                    EncryptionMaterial.symmetric(new byte[16]);
        }

        @Bean
        EncryptionService consumerEncryptionService() {
            return new EncryptionService() {
                @Override
                public String encrypt(String plaintext, EncryptionRequest request) {
                    return plaintext;
                }

                @Override
                public String decrypt(String ciphertext, EncryptionRequest request) {
                    return ciphertext;
                }
            };
        }
    }
}

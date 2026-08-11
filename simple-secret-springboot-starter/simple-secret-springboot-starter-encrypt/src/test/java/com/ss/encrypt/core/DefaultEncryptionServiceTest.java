package com.ss.encrypt.core;

import com.ss.encrypt.algorithm.AesGcmStringEncryptor;
import com.ss.encrypt.algorithm.Base64StringEncryptor;
import com.ss.encrypt.key.PropertyEncryptionKeyProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultEncryptionServiceTest {

    @Test
    void shouldRouteAlgorithmAndResolveKeyById() {
        EncryptionService service = new DefaultEncryptionService(
                List.of(new Base64StringEncryptor(), new AesGcmStringEncryptor()),
                new PropertyEncryptionKeyProvider(Map.of(
                        "primary", EncryptionMaterial.symmetric(new byte[32]))));
        EncryptionRequest request = new EncryptionRequest(
                EncryptionAlgorithm.AES_GCM, CipherEncoding.BASE64, "primary");

        String encrypted = service.encrypt("service-value", request);

        assertThat(service.decrypt(encrypted, request)).isEqualTo("service-value");
    }

    @Test
    void shouldAllowBase64WithoutKeyAndRejectDuplicateAlgorithms() {
        EncryptionService service = new DefaultEncryptionService(
                List.of(new Base64StringEncryptor()),
                (keyId, algorithm) -> {
                    throw new AssertionError("Base64 must not resolve a key");
                });
        EncryptionRequest request = new EncryptionRequest(
                EncryptionAlgorithm.BASE64, CipherEncoding.BASE64, null);

        assertThat(service.decrypt(service.encrypt("value", request), request))
                .isEqualTo("value");
        assertThatThrownBy(() -> new DefaultEncryptionService(
                        List.of(new Base64StringEncryptor(), new Base64StringEncryptor()),
                        (keyId, algorithm) -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BASE64");
    }

    @Test
    void shouldRejectDefaultAlgorithmAndEncoding() {
        EncryptionService service = new DefaultEncryptionService(
                List.of(new Base64StringEncryptor()),
                (keyId, algorithm) -> null);

        assertThatThrownBy(() -> service.encrypt("value", new EncryptionRequest(
                        EncryptionAlgorithm.DEFAULT, CipherEncoding.BASE64, null)))
                .isInstanceOf(EncryptionException.class);
        assertThatThrownBy(() -> service.encrypt("value", new EncryptionRequest(
                        EncryptionAlgorithm.BASE64, CipherEncoding.DEFAULT, null)))
                .isInstanceOf(EncryptionException.class);
    }
}

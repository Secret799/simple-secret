package com.ss.encrypt.key;

import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionMaterial;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertyEncryptionKeyProviderTest {

    @Test
    void shouldResolveTrimmedKeyIdFromDefensiveSnapshot() {
        EncryptionMaterial material = EncryptionMaterial.symmetric(new byte[16]);
        Map<String, EncryptionMaterial> source = new HashMap<>();
        source.put(" primary ", material);

        PropertyEncryptionKeyProvider provider =
                new PropertyEncryptionKeyProvider(source);
        source.clear();

        assertThat(provider.resolve(" primary ", EncryptionAlgorithm.AES_GCM))
                .isSameAs(material);
    }

    @Test
    void shouldRejectMissingKeyWithoutLeakingAvailableIds() {
        PropertyEncryptionKeyProvider provider = new PropertyEncryptionKeyProvider(
                Map.of("production-secret", EncryptionMaterial.symmetric(new byte[16])));

        assertThatThrownBy(() -> provider.resolve(
                        "missing", EncryptionAlgorithm.AES_GCM))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("missing", "AES_GCM")
                .hasMessageNotContaining("production-secret");
    }
}

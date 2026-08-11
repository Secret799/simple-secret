package com.ss.encrypt.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionMaterialTest {

    @Test
    void shouldDefensivelyCopySymmetricKeyAndRedactToString() {
        byte[] source = new byte[] {1, 2, 3, 4};
        EncryptionMaterial material = EncryptionMaterial.symmetric(source);

        source[0] = 9;
        byte[] firstRead = material.secretKey();
        firstRead[1] = 8;

        assertThat(material.secretKey()).containsExactly(1, 2, 3, 4);
        assertThat(material.toString()).doesNotContain("1", "2", "3", "4");
    }

    @Test
    void shouldRequireAtLeastOneAsymmetricKey() {
        assertThatThrownBy(() -> EncryptionMaterial.asymmetric(" ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("publicKey");
    }
}

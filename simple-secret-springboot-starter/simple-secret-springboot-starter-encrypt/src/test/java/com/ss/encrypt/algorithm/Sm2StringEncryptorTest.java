package com.ss.encrypt.algorithm;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionMaterial;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;

class Sm2StringEncryptorTest {

    @Test
    void shouldRoundTripStandardPemKeys() throws Exception {
        KeyPair pair = TestKeyPairs.sm2();
        EncryptionMaterial material = EncryptionMaterial.asymmetric(
                TestKeyPairs.publicPem(pair), TestKeyPairs.privatePem(pair));
        Sm2StringEncryptor encryptor = new Sm2StringEncryptor();

        String encrypted = encryptor.encrypt(
                "sm2-value", material, CipherEncoding.HEX);

        assertThat(encryptor.decrypt(encrypted, material, CipherEncoding.HEX))
                .isEqualTo("sm2-value");
    }
}

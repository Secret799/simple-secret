package com.ss.encrypt.algorithm;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionMaterial;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaOaepStringEncryptorTest {

    @Test
    void shouldRoundTripPemKeysWithOaepSha256() throws Exception {
        KeyPair pair = TestKeyPairs.rsa();
        EncryptionMaterial material = EncryptionMaterial.asymmetric(
                TestKeyPairs.publicPem(pair), TestKeyPairs.privatePem(pair));
        RsaOaepStringEncryptor encryptor = new RsaOaepStringEncryptor();

        String encrypted = encryptor.encrypt(
                "rsa-value", material, CipherEncoding.BASE64);

        assertThat(encryptor.decrypt(encrypted, material, CipherEncoding.BASE64))
                .isEqualTo("rsa-value");
    }

    @Test
    void shouldRejectPayloadThatDoesNotFitRsaBlock() throws Exception {
        KeyPair pair = TestKeyPairs.rsa();
        EncryptionMaterial material = EncryptionMaterial.asymmetric(
                TestKeyPairs.publicPem(pair), TestKeyPairs.privatePem(pair));

        assertThatThrownBy(() -> new RsaOaepStringEncryptor().encrypt(
                        "x".repeat(300), material, CipherEncoding.BASE64))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("too large")
                .hasMessageNotContaining("xxx");
    }
}

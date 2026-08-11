package com.ss.encrypt.algorithm;

import com.ss.encrypt.codec.CiphertextCodec;
import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionMaterial;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Sm4GcmStringEncryptorTest {

    private final Sm4GcmStringEncryptor encryptor = new Sm4GcmStringEncryptor();
    private final EncryptionMaterial material =
            EncryptionMaterial.symmetric(new byte[16]);

    @Test
    void shouldUseRandomNonceAndRejectTampering() {
        String first = encryptor.encrypt("sm4-value", material, CipherEncoding.BASE64);
        String second = encryptor.encrypt("sm4-value", material, CipherEncoding.BASE64);
        byte[] tampered = CiphertextCodec.decode(first, CipherEncoding.BASE64);
        tampered[tampered.length - 1] ^= 1;

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first, material, CipherEncoding.BASE64))
                .isEqualTo("sm4-value");
        assertThatThrownBy(() -> encryptor.decrypt(
                        CiphertextCodec.encode(tampered, CipherEncoding.BASE64),
                        material, CipherEncoding.BASE64))
                .isInstanceOf(EncryptionException.class);
    }
}

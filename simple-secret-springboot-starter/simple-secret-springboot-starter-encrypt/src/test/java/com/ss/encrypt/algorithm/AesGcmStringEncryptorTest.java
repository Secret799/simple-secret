package com.ss.encrypt.algorithm;

import com.ss.encrypt.codec.CiphertextCodec;
import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionMaterial;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmStringEncryptorTest {

    private final AesGcmStringEncryptor encryptor = new AesGcmStringEncryptor();
    private final EncryptionMaterial material =
            EncryptionMaterial.symmetric(sequence(32));

    @Test
    void shouldUseRandomNonceAndRoundTripBothEncodings() {
        String first = encryptor.encrypt("customer-42", material, CipherEncoding.BASE64);
        String second = encryptor.encrypt("customer-42", material, CipherEncoding.BASE64);
        String hex = encryptor.encrypt("customer-42", material, CipherEncoding.HEX);

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first, material, CipherEncoding.BASE64))
                .isEqualTo("customer-42");
        assertThat(encryptor.decrypt(hex, material, CipherEncoding.HEX))
                .isEqualTo("customer-42");
    }

    @Test
    void shouldRejectTamperedCiphertextAndInvalidKeyLength() {
        String encrypted = encryptor.encrypt(
                "protected", material, CipherEncoding.BASE64);
        byte[] tampered = CiphertextCodec.decode(encrypted, CipherEncoding.BASE64);
        tampered[tampered.length - 1] ^= 1;

        assertThatThrownBy(() -> encryptor.decrypt(
                        CiphertextCodec.encode(tampered, CipherEncoding.BASE64),
                        material, CipherEncoding.BASE64))
                .isInstanceOf(EncryptionException.class)
                .hasMessageNotContaining("protected");
        assertThatThrownBy(() -> encryptor.encrypt(
                        "value", EncryptionMaterial.symmetric(new byte[15]),
                        CipherEncoding.BASE64))
                .isInstanceOf(EncryptionException.class)
                .hasMessageContaining("AES_GCM");
    }

    private static byte[] sequence(int length) {
        byte[] result = new byte[length];
        for (int index = 0; index < length; index++) {
            result[index] = (byte) index;
        }
        return result;
    }
}

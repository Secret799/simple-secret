package com.ss.encrypt.algorithm;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Base64StringEncryptorTest {

    @Test
    void shouldEncodeUtf8WithoutRequiringKeyMaterial() {
        Base64StringEncryptor encryptor = new Base64StringEncryptor();

        String encoded = encryptor.encrypt("中文-value", null, CipherEncoding.BASE64);

        assertThat(encryptor.algorithm()).isEqualTo(EncryptionAlgorithm.BASE64);
        assertThat(encryptor.decrypt(encoded, null, CipherEncoding.BASE64))
                .isEqualTo("中文-value");
    }
}

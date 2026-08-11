package com.ss.encrypt.algorithm;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.spi.StringEncryptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Base64 兼容编码器；该算法不提供任何机密性。 */
public final class Base64StringEncryptor implements StringEncryptor {

    @Override
    public EncryptionAlgorithm algorithm() {
        return EncryptionAlgorithm.BASE64;
    }

    @Override
    public String encrypt(
            String plaintext,
            EncryptionMaterial material,
            CipherEncoding encoding) {
        return Base64.getEncoder().encodeToString(
                plaintext.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String decrypt(
            String ciphertext,
            EncryptionMaterial material,
            CipherEncoding encoding) {
        try {
            return new String(Base64.getDecoder().decode(ciphertext),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new EncryptionException("Base64 value is invalid", exception);
        }
    }
}

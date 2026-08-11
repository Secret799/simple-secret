package com.ss.encrypt.algorithm;

import com.ss.encrypt.codec.CiphertextCodec;
import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.spi.StringEncryptor;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;

/** 标准 X.509/PKCS#8 密钥格式的 SM2 字符串加密器。 */
public final class Sm2StringEncryptor implements StringEncryptor {

    private static final byte VERSION = 1;

    @Override
    public EncryptionAlgorithm algorithm() {
        return EncryptionAlgorithm.SM2;
    }

    @Override
    public String encrypt(
            String plaintext,
            EncryptionMaterial material,
            CipherEncoding encoding) {
        PublicKey publicKey = PemKeyParser.publicKey(
                material == null ? null : material.publicKey(),
                "EC", CryptoProviders.BOUNCY_CASTLE);
        try {
            Cipher cipher = Cipher.getInstance(
                    "SM2", CryptoProviders.BOUNCY_CASTLE);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[encrypted.length + 1];
            envelope[0] = VERSION;
            System.arraycopy(encrypted, 0, envelope, 1, encrypted.length);
            return CiphertextCodec.encode(envelope, encoding);
        } catch (Exception exception) {
            throw new EncryptionException("Failed to encrypt using SM2", exception);
        }
    }

    @Override
    public String decrypt(
            String ciphertext,
            EncryptionMaterial material,
            CipherEncoding encoding) {
        PrivateKey privateKey = PemKeyParser.privateKey(
                material == null ? null : material.privateKey(),
                "EC", CryptoProviders.BOUNCY_CASTLE);
        byte[] envelope = CiphertextCodec.decode(ciphertext, encoding);
        if (envelope.length < 2 || envelope[0] != VERSION) {
            throw new EncryptionException("SM2 ciphertext envelope is invalid");
        }
        try {
            Cipher cipher = Cipher.getInstance(
                    "SM2", CryptoProviders.BOUNCY_CASTLE);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(
                    java.util.Arrays.copyOfRange(envelope, 1, envelope.length)),
                    StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new EncryptionException("Failed to decrypt using SM2", exception);
        }
    }
}

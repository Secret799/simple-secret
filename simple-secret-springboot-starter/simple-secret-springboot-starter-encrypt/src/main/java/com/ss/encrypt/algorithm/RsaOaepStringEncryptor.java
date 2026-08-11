package com.ss.encrypt.algorithm;

import com.ss.encrypt.codec.CiphertextCodec;
import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.spi.StringEncryptor;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;

/** RSA OAEP SHA-256 字符串加密器。 */
public final class RsaOaepStringEncryptor implements StringEncryptor {

    private static final byte VERSION = 1;
    private static final int SHA256_BYTES = 32;
    private static final OAEPParameterSpec OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    @Override
    public EncryptionAlgorithm algorithm() {
        return EncryptionAlgorithm.RSA_OAEP_SHA256;
    }

    @Override
    public String encrypt(
            String plaintext,
            EncryptionMaterial material,
            CipherEncoding encoding) {
        PublicKey publicKey = PemKeyParser.publicKey(
                material == null ? null : material.publicKey(), "RSA", null);
        byte[] value = plaintext.getBytes(StandardCharsets.UTF_8);
        int modulusBytes = (((RSAPublicKey) publicKey).getModulus().bitLength() + 7) / 8;
        if (value.length > modulusBytes - 2 * SHA256_BYTES - 2) {
            throw new EncryptionException("RSA plaintext is too large for one OAEP block");
        }
        try {
            Cipher cipher = cipher();
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP);
            return CiphertextCodec.encode(prefix(cipher.doFinal(value)), encoding);
        } catch (Exception exception) {
            throw new EncryptionException("Failed to encrypt using RSA_OAEP_SHA256", exception);
        }
    }

    @Override
    public String decrypt(
            String ciphertext,
            EncryptionMaterial material,
            CipherEncoding encoding) {
        PrivateKey privateKey = PemKeyParser.privateKey(
                material == null ? null : material.privateKey(), "RSA", null);
        byte[] envelope = CiphertextCodec.decode(ciphertext, encoding);
        byte[] encrypted = requireVersion(envelope);
        try {
            Cipher cipher = cipher();
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new EncryptionException("Failed to decrypt using RSA_OAEP_SHA256", exception);
        }
    }

    private static Cipher cipher() throws Exception {
        return Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    }

    private static byte[] prefix(byte[] encrypted) {
        byte[] envelope = new byte[encrypted.length + 1];
        envelope[0] = VERSION;
        System.arraycopy(encrypted, 0, envelope, 1, encrypted.length);
        return envelope;
    }

    private static byte[] requireVersion(byte[] envelope) {
        if (envelope.length < 2 || envelope[0] != VERSION) {
            throw new EncryptionException("RSA ciphertext envelope is invalid");
        }
        return java.util.Arrays.copyOfRange(envelope, 1, envelope.length);
    }
}

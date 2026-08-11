package com.ss.encrypt.algorithm;

import com.ss.encrypt.codec.CiphertextCodec;
import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.spi.StringEncryptor;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Set;

abstract class AbstractAeadStringEncryptor implements StringEncryptor {

    private static final byte VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;

    private final EncryptionAlgorithm algorithm;
    private final String transformation;
    private final String keyAlgorithm;
    private final Set<Integer> keyLengths;
    private final Provider provider;
    private final SecureRandom secureRandom = new SecureRandom();

    AbstractAeadStringEncryptor(
            EncryptionAlgorithm algorithm,
            String transformation,
            String keyAlgorithm,
            Set<Integer> keyLengths,
            Provider provider) {
        this.algorithm = algorithm;
        this.transformation = transformation;
        this.keyAlgorithm = keyAlgorithm;
        this.keyLengths = Set.copyOf(keyLengths);
        this.provider = provider;
    }

    @Override
    public final EncryptionAlgorithm algorithm() {
        return algorithm;
    }

    @Override
    public final String encrypt(
            String plaintext,
            EncryptionMaterial material,
            CipherEncoding encoding) {
        byte[] key = requireKey(material);
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = cipher();
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, keyAlgorithm),
                    new GCMParameterSpec(TAG_BITS, nonce), secureRandom);
            cipher.updateAAD(algorithm.name().getBytes(StandardCharsets.US_ASCII));
            byte[] encrypted = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer envelope = ByteBuffer.allocate(
                    1 + nonce.length + encrypted.length);
            envelope.put(VERSION).put(nonce).put(encrypted);
            return CiphertextCodec.encode(envelope.array(), encoding);
        } catch (Exception exception) {
            throw new EncryptionException(
                    "Failed to encrypt using " + algorithm, exception);
        }
    }

    @Override
    public final String decrypt(
            String ciphertext,
            EncryptionMaterial material,
            CipherEncoding encoding) {
        byte[] key = requireKey(material);
        byte[] envelope = CiphertextCodec.decode(ciphertext, encoding);
        if (envelope.length < 1 + NONCE_BYTES + TAG_BYTES
                || envelope[0] != VERSION) {
            throw new EncryptionException(
                    "Ciphertext envelope is invalid for " + algorithm);
        }
        byte[] nonce = java.util.Arrays.copyOfRange(
                envelope, 1, 1 + NONCE_BYTES);
        byte[] encrypted = java.util.Arrays.copyOfRange(
                envelope, 1 + NONCE_BYTES, envelope.length);
        try {
            Cipher cipher = cipher();
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, keyAlgorithm),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(algorithm.name().getBytes(StandardCharsets.US_ASCII));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new EncryptionException(
                    "Failed to decrypt using " + algorithm, exception);
        }
    }

    private Cipher cipher() throws Exception {
        return provider == null
                ? Cipher.getInstance(transformation)
                : Cipher.getInstance(transformation, provider);
    }

    private byte[] requireKey(EncryptionMaterial material) {
        byte[] key = material == null ? null : material.secretKey();
        if (key == null || !keyLengths.contains(key.length)) {
            throw new EncryptionException(
                    algorithm + " requires key bytes of length " + keyLengths);
        }
        return key;
    }
}

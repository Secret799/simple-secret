package com.ss.encrypt.core;

import com.ss.encrypt.key.EncryptionKeyProvider;
import com.ss.encrypt.spi.StringEncryptor;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/** 使用算法 Bean 和密钥 provider 路由请求的默认实现。 */
public final class DefaultEncryptionService implements EncryptionService {

    private final Map<EncryptionAlgorithm, StringEncryptor> encryptors;
    private final EncryptionKeyProvider keyProvider;

    public DefaultEncryptionService(
            Collection<? extends StringEncryptor> encryptors,
            EncryptionKeyProvider keyProvider) {
        if (encryptors == null || encryptors.isEmpty()) {
            throw new IllegalArgumentException("encryptors must not be empty");
        }
        if (keyProvider == null) {
            throw new IllegalArgumentException("keyProvider must not be null");
        }
        EnumMap<EncryptionAlgorithm, StringEncryptor> indexed =
                new EnumMap<>(EncryptionAlgorithm.class);
        for (StringEncryptor encryptor : encryptors) {
            if (encryptor == null) {
                throw new IllegalArgumentException("encryptor must not be null");
            }
            StringEncryptor previous = indexed.putIfAbsent(
                    encryptor.algorithm(), encryptor);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate StringEncryptor for " + encryptor.algorithm());
            }
        }
        this.encryptors = Map.copyOf(indexed);
        this.keyProvider = keyProvider;
    }

    @Override
    public String encrypt(String plaintext, EncryptionRequest request) {
        return encryptor(request).encrypt(
                requireValue(plaintext, "Plaintext"),
                material(request), request.encoding());
    }

    @Override
    public String decrypt(String ciphertext, EncryptionRequest request) {
        return encryptor(request).decrypt(
                requireValue(ciphertext, "Ciphertext"),
                material(request), request.encoding());
    }

    private StringEncryptor encryptor(EncryptionRequest request) {
        validateRequest(request);
        StringEncryptor encryptor = encryptors.get(request.algorithm());
        if (encryptor == null) {
            throw new EncryptionException(
                    "No StringEncryptor is registered for " + request.algorithm());
        }
        return encryptor;
    }

    private EncryptionMaterial material(EncryptionRequest request) {
        if (request.algorithm() == EncryptionAlgorithm.BASE64) {
            return null;
        }
        return keyProvider.resolve(request.keyId(), request.algorithm());
    }

    private static void validateRequest(EncryptionRequest request) {
        if (request == null || request.algorithm() == null
                || request.algorithm() == EncryptionAlgorithm.DEFAULT) {
            throw new EncryptionException("Concrete encryption algorithm is required");
        }
        if (request.encoding() == null
                || request.encoding() == CipherEncoding.DEFAULT) {
            throw new EncryptionException("Concrete cipher encoding is required");
        }
    }

    private static String requireValue(String value, String label) {
        if (value == null) {
            throw new EncryptionException(label + " must not be null");
        }
        return value;
    }
}

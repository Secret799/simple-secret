package com.ss.encrypt.algorithm;

import com.ss.encrypt.core.EncryptionException;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class PemKeyParser {

    private PemKeyParser() {
    }

    static PublicKey publicKey(
            String value, String algorithm, Provider provider) {
        if (value == null || value.isBlank()) {
            throw new EncryptionException("Public key is required");
        }
        try {
            return keyFactory(algorithm, provider).generatePublic(
                    new X509EncodedKeySpec(decode(value)));
        } catch (Exception exception) {
            throw new EncryptionException("Public key format is invalid", exception);
        }
    }

    static PrivateKey privateKey(
            String value, String algorithm, Provider provider) {
        if (value == null || value.isBlank()) {
            throw new EncryptionException("Private key is required");
        }
        try {
            return keyFactory(algorithm, provider).generatePrivate(
                    new PKCS8EncodedKeySpec(decode(value)));
        } catch (Exception exception) {
            throw new EncryptionException("Private key format is invalid", exception);
        }
    }

    private static KeyFactory keyFactory(
            String algorithm, Provider provider) throws Exception {
        return provider == null
                ? KeyFactory.getInstance(algorithm)
                : KeyFactory.getInstance(algorithm, provider);
    }

    private static byte[] decode(String value) {
        String normalized = value
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}

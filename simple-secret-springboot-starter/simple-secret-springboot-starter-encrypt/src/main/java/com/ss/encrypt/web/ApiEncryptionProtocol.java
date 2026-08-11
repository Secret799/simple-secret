package com.ss.encrypt.web;

import com.ss.encrypt.algorithm.AesGcmStringEncryptor;
import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.core.EncryptionRequest;
import com.ss.encrypt.core.EncryptionService;

import java.security.SecureRandom;
import java.util.Base64;

/** RSA-OAEP + AES-GCM 组成的 API v1 混合加密协议。 */
public final class ApiEncryptionProtocol {

    private static final String VERSION_PREFIX = "v1.";
    private static final int AES_KEY_BYTES = 32;

    private final EncryptionService encryptionService;
    private final AesGcmStringEncryptor bodyEncryptor = new AesGcmStringEncryptor();
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiEncryptionProtocol(EncryptionService encryptionService) {
        this.encryptionService = java.util.Objects.requireNonNull(
                encryptionService, "encryptionService");
    }

    /** 使用指定 RSA key id 生成一次性 AES key 并加密正文。 */
    public ApiEncryptedPayload encrypt(String plaintext, String rsaKeyId) {
        byte[] aesKey = new byte[AES_KEY_BYTES];
        secureRandom.nextBytes(aesKey);
        String encodedKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(aesKey);
        String wrappedKey = encryptionService.encrypt(encodedKey,
                new EncryptionRequest(EncryptionAlgorithm.RSA_OAEP_SHA256,
                        CipherEncoding.BASE64, rsaKeyId));
        String encryptedBody = bodyEncryptor.encrypt(
                plaintext, EncryptionMaterial.symmetric(aesKey),
                CipherEncoding.BASE64);
        return new ApiEncryptedPayload(
                externalize(wrappedKey), externalize(encryptedBody));
    }

    /** 使用指定 RSA key id 解包 AES key 并解密正文。 */
    public String decrypt(
            String keyHeader, String body, String rsaKeyId) {
        try {
            String encodedKey = encryptionService.decrypt(
                    internalize(keyHeader),
                    new EncryptionRequest(EncryptionAlgorithm.RSA_OAEP_SHA256,
                            CipherEncoding.BASE64, rsaKeyId));
            byte[] aesKey = Base64.getUrlDecoder().decode(encodedKey);
            if (aesKey.length != AES_KEY_BYTES) {
                throw new EncryptionException("API v1 AES key length is invalid");
            }
            return bodyEncryptor.decrypt(
                    internalize(body), EncryptionMaterial.symmetric(aesKey),
                    CipherEncoding.BASE64);
        } catch (EncryptionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EncryptionException("API v1 payload is invalid", exception);
        }
    }

    private static String externalize(String standardBase64) {
        byte[] value = Base64.getDecoder().decode(standardBase64);
        return VERSION_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value);
    }

    private static String internalize(String externalValue) {
        if (externalValue == null || !externalValue.startsWith(VERSION_PREFIX)) {
            throw new EncryptionException("Unsupported API encryption version");
        }
        try {
            byte[] value = Base64.getUrlDecoder().decode(
                    externalValue.substring(VERSION_PREFIX.length()));
            return Base64.getEncoder().encodeToString(value);
        } catch (IllegalArgumentException exception) {
            throw new EncryptionException("API v1 payload encoding is invalid", exception);
        }
    }
}

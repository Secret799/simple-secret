package com.ss.encrypt.core;

/** 单次字符串加解密所需的算法、编码和逻辑密钥标识。 */
public record EncryptionRequest(
        EncryptionAlgorithm algorithm,
        CipherEncoding encoding,
        String keyId) {
}

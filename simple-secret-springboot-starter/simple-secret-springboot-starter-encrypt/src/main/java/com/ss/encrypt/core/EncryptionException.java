package com.ss.encrypt.core;

/** 加解密、密钥解析或密文校验失败。 */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}

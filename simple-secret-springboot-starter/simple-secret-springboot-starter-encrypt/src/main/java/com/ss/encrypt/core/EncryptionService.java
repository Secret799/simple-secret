package com.ss.encrypt.core;

/** 面向应用和框架集成的统一字符串加解密服务。 */
public interface EncryptionService {

    /** 按请求配置加密明文。 */
    String encrypt(String plaintext, EncryptionRequest request);

    /** 按请求配置解密密文。 */
    String decrypt(String ciphertext, EncryptionRequest request);
}

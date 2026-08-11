package com.ss.encrypt.core;

/** Encrypt starter 支持的字符串处理算法。 */
public enum EncryptionAlgorithm {

    /** 由上层配置选择实际算法。 */
    DEFAULT,

    /** Base64 编码，不提供机密性。 */
    BASE64,

    /** AES GCM authenticated encryption。 */
    AES_GCM,

    /** RSA OAEP SHA-256 非对称加密。 */
    RSA_OAEP_SHA256,

    /** 国密 SM2 非对称加密。 */
    SM2,

    /** 国密 SM4 GCM authenticated encryption。 */
    SM4_GCM
}

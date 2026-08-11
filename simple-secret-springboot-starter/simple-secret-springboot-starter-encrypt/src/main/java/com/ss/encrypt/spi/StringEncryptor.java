package com.ss.encrypt.spi;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionMaterial;

/** 单个算法的字符串加解密扩展点。 */
public interface StringEncryptor {

    /** 返回该实现处理的算法。 */
    EncryptionAlgorithm algorithm();

    /** 加密 UTF-8 明文。 */
    String encrypt(
            String plaintext,
            EncryptionMaterial material,
            CipherEncoding encoding);

    /** 解密为 UTF-8 明文。 */
    String decrypt(
            String ciphertext,
            EncryptionMaterial material,
            CipherEncoding encoding);
}

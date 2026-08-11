package com.ss.encrypt.key;

import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionMaterial;

/** 按 key id 为算法提供密钥材料的扩展点。 */
@FunctionalInterface
public interface EncryptionKeyProvider {

    /**
     * 解析指定算法使用的密钥材料。
     *
     * @param keyId 逻辑密钥标识
     * @param algorithm 实际算法
     * @return 对应密钥材料
     */
    EncryptionMaterial resolve(String keyId, EncryptionAlgorithm algorithm);
}

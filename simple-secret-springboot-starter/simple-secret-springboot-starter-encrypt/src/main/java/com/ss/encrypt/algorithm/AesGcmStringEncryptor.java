package com.ss.encrypt.algorithm;

import com.ss.encrypt.core.EncryptionAlgorithm;

import java.util.Set;

/** AES/GCM/NoPadding 字符串加密器。 */
public final class AesGcmStringEncryptor extends AbstractAeadStringEncryptor {

    public AesGcmStringEncryptor() {
        super(EncryptionAlgorithm.AES_GCM, "AES/GCM/NoPadding", "AES",
                Set.of(16, 24, 32), null);
    }
}

package com.ss.encrypt.algorithm;

import com.ss.encrypt.core.EncryptionAlgorithm;

import java.util.Set;

/** SM4/GCM/NoPadding 字符串加密器。 */
public final class Sm4GcmStringEncryptor extends AbstractAeadStringEncryptor {

    public Sm4GcmStringEncryptor() {
        super(EncryptionAlgorithm.SM4_GCM, "SM4/GCM/NoPadding", "SM4",
                Set.of(16), CryptoProviders.BOUNCY_CASTLE);
    }
}

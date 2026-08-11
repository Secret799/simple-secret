package com.ss.encrypt.mybatis;

import com.ss.encrypt.algorithm.AesGcmStringEncryptor;
import com.ss.encrypt.config.EncryptProperties;
import com.ss.encrypt.core.DefaultEncryptionService;
import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.key.PropertyEncryptionKeyProvider;

import java.util.List;
import java.util.Map;

final class TestProcessors {

    private TestProcessors() {
    }

    static EncryptedObjectProcessor processor() {
        return new EncryptedObjectProcessor(
                new DefaultEncryptionService(
                        List.of(new AesGcmStringEncryptor()),
                        new PropertyEncryptionKeyProvider(Map.of(
                                "default", EncryptionMaterial.symmetric(new byte[32])))),
                new EncryptProperties.Mybatis());
    }
}

package com.ss.encrypt.mybatis;

import com.ss.encrypt.annotation.EncryptField;
import com.ss.encrypt.config.EncryptProperties;
import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionRequest;

import java.lang.reflect.Field;

record EncryptedFieldMetadata(Field field, EncryptField annotation) {

    EncryptionRequest request(EncryptProperties.Mybatis defaults) {
        EncryptionAlgorithm algorithm = annotation.algorithm()
                == EncryptionAlgorithm.DEFAULT
                ? defaults.getAlgorithm() : annotation.algorithm();
        CipherEncoding encoding = annotation.encoding() == CipherEncoding.DEFAULT
                ? defaults.getEncoding() : annotation.encoding();
        String keyId = annotation.keyId().trim().isEmpty()
                ? defaults.getKeyId() : annotation.keyId().trim();
        return new EncryptionRequest(algorithm, encoding, keyId);
    }
}

package com.ss.encrypt.config;

import com.ss.encrypt.algorithm.AesGcmStringEncryptor;
import com.ss.encrypt.algorithm.Base64StringEncryptor;
import com.ss.encrypt.algorithm.RsaOaepStringEncryptor;
import com.ss.encrypt.algorithm.Sm2StringEncryptor;
import com.ss.encrypt.algorithm.Sm4GcmStringEncryptor;
import com.ss.encrypt.core.DefaultEncryptionService;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionService;
import com.ss.encrypt.key.EncryptionKeyProvider;
import com.ss.encrypt.key.PropertyEncryptionKeyProvider;
import com.ss.encrypt.spi.StringEncryptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.EnumMap;

/** Encrypt starter 的核心密码服务自动配置。 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "simple-secret.encrypt",
        name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(EncryptProperties.class)
public class SimpleSecretEncryptAutoConfiguration {

    /** 使用外部配置创建默认密钥 provider。 */
    @Bean
    @ConditionalOnMissingBean
    EncryptionKeyProvider encryptionKeyProvider(EncryptProperties properties) {
        return new PropertyEncryptionKeyProvider(properties.materials());
    }

    /** 创建内置算法齐全且允许应用按算法覆盖的统一服务。 */
    @Bean
    @ConditionalOnMissingBean
    EncryptionService encryptionService(
            EncryptionKeyProvider keyProvider,
            ObjectProvider<StringEncryptor> customEncryptors) {
        EnumMap<EncryptionAlgorithm, StringEncryptor> encryptors =
                new EnumMap<>(EncryptionAlgorithm.class);
        put(encryptors, new Base64StringEncryptor());
        put(encryptors, new AesGcmStringEncryptor());
        put(encryptors, new RsaOaepStringEncryptor());
        put(encryptors, new Sm2StringEncryptor());
        put(encryptors, new Sm4GcmStringEncryptor());
        customEncryptors.orderedStream().forEach(
                encryptor -> encryptors.put(encryptor.algorithm(), encryptor));
        return new DefaultEncryptionService(encryptors.values(), keyProvider);
    }

    private static void put(
            EnumMap<EncryptionAlgorithm, StringEncryptor> encryptors,
            StringEncryptor encryptor) {
        encryptors.put(encryptor.algorithm(), encryptor);
    }
}

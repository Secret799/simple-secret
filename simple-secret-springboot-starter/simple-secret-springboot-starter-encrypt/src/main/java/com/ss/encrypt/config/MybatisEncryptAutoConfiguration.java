package com.ss.encrypt.config;

import com.ss.encrypt.core.EncryptionService;
import com.ss.encrypt.mybatis.EncryptedObjectProcessor;
import com.ss.encrypt.mybatis.MybatisDecryptInterceptor;
import com.ss.encrypt.mybatis.MybatisEncryptInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** 可选 MyBatis 字段加解密自动配置。 */
@AutoConfiguration(after = SimpleSecretEncryptAutoConfiguration.class)
@ConditionalOnClass(Interceptor.class)
@ConditionalOnBean(EncryptionService.class)
@ConditionalOnProperty(
        prefix = "simple-secret.encrypt",
        name = {"enabled", "mybatis.enabled"},
        havingValue = "true")
public class MybatisEncryptAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    EncryptedObjectProcessor encryptedObjectProcessor(
            EncryptionService encryptionService,
            EncryptProperties properties) {
        return new EncryptedObjectProcessor(
                encryptionService, properties.getMybatis());
    }

    @Bean
    @ConditionalOnMissingBean
    MybatisEncryptInterceptor mybatisEncryptInterceptor(
            EncryptedObjectProcessor processor) {
        return new MybatisEncryptInterceptor(processor);
    }

    @Bean
    @ConditionalOnMissingBean
    MybatisDecryptInterceptor mybatisDecryptInterceptor(
            EncryptedObjectProcessor processor) {
        return new MybatisDecryptInterceptor(processor);
    }
}

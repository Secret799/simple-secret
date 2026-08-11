package com.ss.encrypt.annotation;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记需要由 MyBatis interceptor 加解密的 String 字段。 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EncryptField {

    /** 字段算法；DEFAULT 使用 MyBatis 全局配置。 */
    EncryptionAlgorithm algorithm() default EncryptionAlgorithm.DEFAULT;

    /** 密文编码；DEFAULT 使用 MyBatis 全局配置。 */
    CipherEncoding encoding() default CipherEncoding.DEFAULT;

    /** 逻辑密钥标识；空字符串使用 MyBatis 全局配置。 */
    String keyId() default "";
}

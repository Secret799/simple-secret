package com.ss.encrypt.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为单个 Controller 类型或方法启用 API 请求/响应密文传输。 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiEncrypt {

    /** 是否要求 POST、PUT、PATCH 请求体使用 v1 协议加密。 */
    boolean request() default true;

    /** 是否使用 v1 协议加密响应体。 */
    boolean response() default false;
}

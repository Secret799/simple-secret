package com.ss.encrypt.web;

import com.ss.encrypt.annotation.ApiEncrypt;
import jakarta.servlet.http.HttpServletRequest;

/** 从当前 Servlet 请求解析实际 Controller 的 API 加密注解。 */
@FunctionalInterface
public interface ApiEncryptAnnotationResolver {

    /** 未标注时返回 {@code null}。 */
    ApiEncrypt resolve(HttpServletRequest request) throws Exception;
}

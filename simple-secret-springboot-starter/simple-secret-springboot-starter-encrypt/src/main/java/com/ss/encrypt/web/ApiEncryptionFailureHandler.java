package com.ss.encrypt.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** 允许应用把 API 加密失败映射到自己的错误响应格式。 */
@FunctionalInterface
public interface ApiEncryptionFailureHandler {

    /** 处理失败；原因不携带密钥、明文或密文。 */
    void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            ApiEncryptionFailureReason reason) throws IOException;
}

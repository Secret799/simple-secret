package com.ss.encrypt.web;

/** API 请求或响应超过 starter 配置的内存缓冲上限。 */
public final class ApiPayloadTooLargeException extends RuntimeException {

    public ApiPayloadTooLargeException() {
        super("Encrypted API payload exceeds configured limit");
    }
}

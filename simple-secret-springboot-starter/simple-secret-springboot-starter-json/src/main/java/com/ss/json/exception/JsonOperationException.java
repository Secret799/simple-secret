package com.ss.json.exception;

import java.io.Serial;

/**
 * JSON 序列化或反序列化失败时抛出的统一异常。
 */
public class JsonOperationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建 JSON 操作异常。
     *
     * @param message 不包含原始 JSON 的安全错误信息
     * @param cause   底层 Jackson 异常
     */
    public JsonOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

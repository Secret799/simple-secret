package com.ss.zlm4j.exception;

import java.io.Serial;

/**
 * ZLMediaKit(zlm4j) 媒体操作失败时抛出的安全运行时异常。
 *
 * <p>迁移自 honeybee 的 {@code ServiceException}/{@code BusinessException}，统一收口为单一异常类型。</p>
 */
public class ZlmOperationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建 zlm 操作异常。
     *
     * @param message 异常消息
     */
    public ZlmOperationException(String message) {
        super(message);
    }

    /**
     * 创建 zlm 操作异常。
     *
     * @param message 异常消息
     * @param cause   底层异常
     */
    public ZlmOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

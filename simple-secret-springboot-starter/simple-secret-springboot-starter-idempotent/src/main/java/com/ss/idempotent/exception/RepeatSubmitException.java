package com.ss.idempotent.exception;

/** 重复提交或幂等配置无效时抛出的异常。 */
public class RepeatSubmitException extends RuntimeException {

    /**
     * 创建异常。
     *
     * @param message 可安全返回给调用方的消息
     */
    public RepeatSubmitException(String message) {
        super(message);
    }

    /**
     * 创建带原因的异常。
     *
     * @param message 异常消息
     * @param cause 原始原因
     */
    public RepeatSubmitException(String message, Throwable cause) {
        super(message, cause);
    }
}

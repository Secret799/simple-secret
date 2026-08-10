package com.ss.nats.exception;

/**
 * NATS 连接、发布、请求、订阅或关闭操作失败时抛出的统一异常。
 */
public class NatsOperationException extends RuntimeException {

    /**
     * @param message 不包含消息体或凭据的错误描述
     */
    public NatsOperationException(String message) {
        super(message);
    }

    /**
     * @param message 不包含消息体或凭据的错误描述
     * @param cause 原始异常
     */
    public NatsOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

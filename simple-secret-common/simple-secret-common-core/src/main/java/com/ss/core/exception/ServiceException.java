package com.ss.core.exception;

import java.io.Serial;

/** 服务执行失败时抛出的异常。 */
public final class ServiceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Integer code;
    private String detailMessage;

    /**
     * 使用消息创建异常。
     *
     * @param message 错误消息
     */
    public ServiceException(String message) {
        super(message);
        this.code = null;
    }

    /**
     * 使用消息和错误码创建异常。
     *
     * @param message 错误消息
     * @param code 数值错误码
     */
    public ServiceException(String message, int code) {
        this(message, code, new Object[0]);
    }

    /**
     * 使用消息模板、错误码和参数创建异常。
     *
     * @param message 支持 {@code {}} 占位符的消息模板
     * @param code 错误码
     * @param arguments 模板参数
     */
    public ServiceException(String message, int code, Object... arguments) {
        super(MessageFormatter.format(message, arguments));
        this.code = code;
    }

    /**
     * 使用消息和原始原因创建异常。
     *
     * @param message 错误消息
     * @param cause 原始异常
     */
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    /**
     * 使用原始原因创建异常。
     *
     * @param cause 原始异常
     */
    public ServiceException(Throwable cause) {
        super(cause);
        this.code = null;
    }

    /**
     * 返回错误码。
     *
     * @return 数值错误码；未设置时返回 null
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 返回仅用于内部诊断的错误详情。
     *
     * @return 内部诊断信息；未设置时返回 null
     */
    public String getDetailMessage() {
        return detailMessage;
    }

    /**
     * 设置仅用于内部诊断的错误详情。
     *
     * @param detailMessage 错误详情
     * @return 当前异常
     */
    public ServiceException setDetailMessage(String detailMessage) {
        this.detailMessage = detailMessage;
        return this;
    }
}

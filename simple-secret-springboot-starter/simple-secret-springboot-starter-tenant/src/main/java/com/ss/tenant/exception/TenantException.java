package com.ss.tenant.exception;

import java.io.Serial;

/** 当前调用缺少有效租户信息时抛出的异常。 */
public class TenantException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建租户异常。
     *
     * @param message 不包含敏感上下文的错误消息
     */
    public TenantException(String message) {
        super(message);
    }
}

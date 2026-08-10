package com.ss.common.toolbox.property;

import java.io.Serial;

/**
 * 无法从方法引用解析 JavaBean 属性时抛出的异常。
 */
public class LambdaResolutionException extends IllegalArgumentException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建属性解析异常。
     *
     * @param message 错误信息
     */
    public LambdaResolutionException(String message) {
        super(message);
    }

    /**
     * 创建带底层原因的属性解析异常。
     *
     * @param message 错误信息
     * @param cause   底层原因
     */
    public LambdaResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

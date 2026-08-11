package com.ss.core.exception;

import java.io.Serial;

/** 业务规则不满足时抛出的异常。 */
public final class BusinessException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private BusinessException(String module, String code, Object[] arguments, String message) {
        super(module, code, arguments, message);
    }

    /**
     * 创建待国际化的业务异常。
     *
     * @param code 国际化错误码
     * @param arguments 消息参数
     * @return 业务异常
     */
    public static BusinessException i18n(String code, Object... arguments) {
        return new BusinessException(null, code, arguments, null);
    }

    /**
     * 创建带模块信息、待国际化的业务异常。
     *
     * @param module 所属模块
     * @param code 国际化错误码
     * @param arguments 消息参数
     * @return 业务异常
     */
    public static BusinessException i18nForModule(
            String module, String code, Object... arguments) {
        return new BusinessException(module, code, arguments, null);
    }

    /**
     * 创建普通消息业务异常。
     *
     * @param message 支持 {@code {}} 占位符的消息模板
     * @param arguments 模板参数
     * @return 业务异常
     */
    public static BusinessException normal(String message, Object... arguments) {
        return new BusinessException(null, null, null, MessageFormatter.format(message, arguments));
    }

    /**
     * 创建带模块信息的普通消息业务异常。
     *
     * @param module 所属模块
     * @param message 支持 {@code {}} 占位符的消息模板
     * @param arguments 模板参数
     * @return 业务异常
     */
    public static BusinessException normalForModule(
            String module, String message, Object... arguments) {
        return new BusinessException(module, null, null, MessageFormatter.format(message, arguments));
    }
}

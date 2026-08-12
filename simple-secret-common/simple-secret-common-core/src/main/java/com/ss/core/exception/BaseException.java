package com.ss.core.exception;

import java.io.Serial;

/**
 * 带模块、错误码和消息参数的基础业务异常。
 *
 * <p>该类型只保存国际化所需的数据，不依赖或访问 Spring 消息源。</p>
 */
public class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String module;
    private final String code;
    private final Object[] arguments;

    /**
     * 创建基础异常。
     *
     * @param module 所属模块，可以为 null
     * @param code 国际化错误码，可以为 null
     * @param arguments 国际化参数，可以为 null
     * @param defaultMessage 默认消息，可以为 null
     */
    protected BaseException(String module, String code, Object[] arguments, String defaultMessage) {
        this(module, code, arguments, defaultMessage, null);
    }

    /**
     * 创建带原因的基础异常。
     *
     * @param module 所属模块，可以为 null
     * @param code 国际化错误码，可以为 null
     * @param arguments 国际化参数，可以为 null
     * @param defaultMessage 默认消息，可以为 null
     * @param cause 原始异常，可以为 null
     */
    protected BaseException(
            String module, String code, Object[] arguments, String defaultMessage, Throwable cause) {
        super(defaultMessage != null && !defaultMessage.isBlank() ? defaultMessage : code, cause);
        this.module = module;
        this.code = code;
        this.arguments = arguments == null ? new Object[0] : arguments.clone();
    }

    /**
     * 返回所属模块。
     *
     * @return 模块标识；未设置时返回 null
     */
    public String getModule() {
        return module;
    }

    /**
     * 返回国际化错误码。
     *
     * @return 国际化错误码；普通消息异常返回 null
     */
    public String getCode() {
        return code;
    }

    /**
     * 返回国际化参数的副本。
     *
     * @return 不可影响内部状态的参数数组
     */
    public Object[] getArguments() {
        return arguments.clone();
    }
}

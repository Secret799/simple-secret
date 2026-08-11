package com.ss.auth.exception;

import java.io.Serial;
import java.util.Objects;

/**
 * 认证过程中发生的固定语义异常。
 */
public class AuthException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /**
     * 按固定失败原因创建认证异常。
     *
     * @param reason 认证失败原因
     */
    public AuthException(Reason reason) {
        super(Objects.requireNonNull(reason, "reason").getMessage());
        this.reason = reason;
    }

    /**
     * 获取认证失败原因。
     *
     * @return 固定失败原因
     */
    public Reason getReason() {
        return reason;
    }

    /**
     * 认证失败原因及其对外固定响应信息。
     */
    public enum Reason {
        /** 请求字段缺失或格式不符合精确匹配规则。 */
        INVALID_REQUEST(400, "认证请求无效"),
        /** 请求的授权方式没有注册或未被客户端允许。 */
        UNSUPPORTED_GRANT(400, "认证方式不受支持"),
        /** 客户端不存在或不可用。 */
        CLIENT_UNAVAILABLE(401, "认证失败"),
        /** 当前请求未完成有效认证。 */
        UNAUTHENTICATED(401, "认证失败");

        private final int status;
        private final String message;

        Reason(int status, String message) {
            this.status = status;
            this.message = message;
        }

        /**
         * 获取对应的 HTTP 状态码。
         *
         * @return HTTP 状态码
         */
        public int getStatus() {
            return status;
        }

        /**
         * 获取固定且不包含用户输入的错误消息。
         *
         * @return 固定错误消息
         */
        public String getMessage() {
            return message;
        }
    }
}

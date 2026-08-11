package com.ss.core.http;

/**
 * Simple Secret 使用的 HTTP 状态码常量。
 *
 * <p>业务警告码 {@link #WARNING} 不属于标准 HTTP 状态码，仅用于应用响应体。</p>
 */
public final class HttpStatusCodes {

    /** 请求成功。 */
    public static final int OK = 200;
    /** 资源创建成功。 */
    public static final int CREATED = 201;
    /** 请求已接受。 */
    public static final int ACCEPTED = 202;
    /** 请求成功但没有响应内容。 */
    public static final int NO_CONTENT = 204;
    /** 永久重定向。 */
    public static final int MOVED_PERMANENTLY = 301;
    /** 查看其他资源。 */
    public static final int SEE_OTHER = 303;
    /** 资源未修改。 */
    public static final int NOT_MODIFIED = 304;
    /** 请求参数错误。 */
    public static final int BAD_REQUEST = 400;
    /** 未认证。 */
    public static final int UNAUTHORIZED = 401;
    /** 无权访问。 */
    public static final int FORBIDDEN = 403;
    /** 资源不存在。 */
    public static final int NOT_FOUND = 404;
    /** 请求方法不允许。 */
    public static final int METHOD_NOT_ALLOWED = 405;
    /** 资源冲突。 */
    public static final int CONFLICT = 409;
    /** 不支持的媒体类型。 */
    public static final int UNSUPPORTED_MEDIA_TYPE = 415;
    /** 服务器内部错误。 */
    public static final int INTERNAL_SERVER_ERROR = 500;
    /** 功能未实现。 */
    public static final int NOT_IMPLEMENTED = 501;
    /** 应用级警告。 */
    public static final int WARNING = 601;

    private HttpStatusCodes() {
    }
}

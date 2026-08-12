package com.ss.easymedia.webrtc.exception;

import org.springframework.http.HttpStatus;

/**
 * WebRTC 会话协议异常。
 */
public class WebRtcSessionException extends RuntimeException {

    /** 返回给客户端的 HTTP 状态。 */
    private final HttpStatus status;
    /** 稳定的业务错误码。 */
    private final String errorCode;

    /**
     * 创建可被统一异常处理器转换为 HTTP 响应的异常。

     *
     * @param status 状态
     * @param errorCode 稳定错误码
     * @param message 消息
     */
    public WebRtcSessionException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * 返回状态。
     *
     * @return 状态
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 返回稳定错误码。
     *
     * @return 稳定错误码
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * @return 认证失败异常。
     *
     * @param code 业务编码
     */
    public static WebRtcSessionException unauthorized(String code) {
        return new WebRtcSessionException(HttpStatus.UNAUTHORIZED, code, "WebRTC authentication required");
    }

    /**
     * @return 无会话访问权限异常。
     *
     * @param code 业务编码
     */
    public static WebRtcSessionException forbidden(String code) {
        return new WebRtcSessionException(HttpStatus.FORBIDDEN, code, "WebRTC session access denied");
    }

    /**
     * @return 请求参数或 SDP 不合法异常。
     *
     * @param code 业务编码
     * @param message 消息
     */
    public static WebRtcSessionException badRequest(String code, String message) {
        return new WebRtcSessionException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * @return 会话状态冲突异常。
     *
     * @param code 业务编码
     * @param message 消息
     */
    public static WebRtcSessionException conflict(String code, String message) {
        return new WebRtcSessionException(HttpStatus.CONFLICT, code, message);
    }

    /**
     * @return 会话不存在异常。
     *
     * @param code 业务编码
     */
    public static WebRtcSessionException notFound(String code) {
        return new WebRtcSessionException(HttpStatus.NOT_FOUND, code, "WebRTC session not found");
    }

    /**
     * @return 信令限流异常。
     *
     * @param code 业务编码
     */
    public static WebRtcSessionException tooManyRequests(String code) {
        return new WebRtcSessionException(HttpStatus.TOO_MANY_REQUESTS, code, "WebRTC request rate exceeded");
    }

    /**
     * @return ZLM 上游调用失败异常。
     *
     * @param code 业务编码
     * @param message 消息
     */
    public static WebRtcSessionException badGateway(String code, String message) {
        return new WebRtcSessionException(HttpStatus.BAD_GATEWAY, code, message);
    }

    /**
     * @return Redis 或会话服务不可用异常。
     *
     * @param code 业务编码
     */
    public static WebRtcSessionException serviceUnavailable(String code) {
        return new WebRtcSessionException(HttpStatus.SERVICE_UNAVAILABLE, code, "WebRTC service unavailable");
    }
}

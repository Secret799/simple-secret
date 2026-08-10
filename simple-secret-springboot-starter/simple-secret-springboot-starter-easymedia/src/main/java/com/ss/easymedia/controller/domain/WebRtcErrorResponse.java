package com.ss.easymedia.controller.domain;

/**
 * WebRTC HTTP 错误响应。
 *
 * @param code      稳定错误码
 * @param message   错误信息
 * @param requestId 请求标识
 */
public record WebRtcErrorResponse(String code, String message, String requestId) {
}

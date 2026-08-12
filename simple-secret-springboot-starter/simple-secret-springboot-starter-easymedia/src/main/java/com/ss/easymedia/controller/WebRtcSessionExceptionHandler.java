package com.ss.easymedia.controller;

import com.ss.easymedia.controller.domain.WebRtcErrorResponse;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 为 WHIP/WHEP 客户端返回真实 HTTP 状态码。
 */
@RestControllerAdvice(assignableTypes = Zlm4jWebRTCController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebRtcSessionExceptionHandler {

    /**
     * 将领域会话异常转换为带稳定错误码的 HTTP 响应。

     *
     * @param exception 异常对象
     * @param request 请求对象
     * @return 返回的 {@code ResponseEntity<WebRtcErrorResponse>} 结果
     */
    @ExceptionHandler(WebRtcSessionException.class)
    public ResponseEntity<WebRtcErrorResponse> handleWebRtcSessionException(
            WebRtcSessionException exception, HttpServletRequest request) {
        return error(exception.getStatus(), exception.getErrorCode(),
                exception.getMessage(), request);
    }

    /**
     * 将缺少 app、stream 等必填参数转换为 400 响应。

     *
     * @param exception 异常对象
     * @param request 请求对象
     * @return 返回的 {@code ResponseEntity<WebRtcErrorResponse>} 结果
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<WebRtcErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "WEBRTC_REQUEST_PARAMETER_MISSING",
                exception.getParameterName() + " is required", request);
    }

    /**
     * 将不支持的 SDP 媒体类型转换为 415 响应。

     *
     * @param exception 异常对象
     * @param request 请求对象
     * @return 返回的 {@code ResponseEntity<WebRtcErrorResponse>} 结果
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<WebRtcErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "WEBRTC_CONTENT_TYPE_UNSUPPORTED",
                "Unsupported WebRTC content type", request);
    }

    /**
     * 将无法反序列化或参数类型不匹配的请求转换为 400 响应。

     *
     * @param exception 异常对象
     * @param request 请求对象
     * @return 返回的 {@code ResponseEntity<WebRtcErrorResponse>} 结果
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<WebRtcErrorResponse> handleInvalidRequest(
            Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "WEBRTC_REQUEST_INVALID",
                "Invalid WebRTC request", request);
    }

    /**
     * 构造禁用缓存的 JSON 错误响应。
     */
    private ResponseEntity<WebRtcErrorResponse> error(HttpStatus status, String code,
                                                       String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new WebRtcErrorResponse(code, message, requestId(request)));
    }

    /**
     * 优先从日志 MDC 获取链路标识，缺失时回退至请求头。
     */
    private String requestId(HttpServletRequest request) {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String header = request.getHeader("X-Request-Id");
        return header == null || header.isBlank() ? null : header;
    }
}

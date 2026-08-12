package com.ss.easymedia.webrtc.domain;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

import java.net.URI;

/**
 * ZLM 信令调用结果。
 *
 * @param requestUri 请求或本地 C API 对应的 RTC 地址
 * @param status 上游或本地适配后的 HTTP 状态
 * @param headers 可安全转发的响应头
 * @param body SDP Answer 或上游错误响应体
 * @param managedSession 是否拥有可 PATCH/DELETE 的受管会话资源
 */
public record ZlmWebRtcResponse(URI requestUri, HttpStatusCode status, HttpHeaders headers, byte[] body,
                                boolean managedSession) {

    /**
     * 创建具备上游会话资源的 HTTP 信令响应。

     *
     * @param requestUri 请求 URI
     * @param status 状态
     * @param headers 表头或消息头集合
     * @param body 请求或响应体
     */
    public ZlmWebRtcResponse(URI requestUri, HttpStatusCode status, HttpHeaders headers, byte[] body) {
        this(requestUri, status, headers, body, true);
    }

    /**
     * 防御性复制可变响应数据，避免调用方在构造后篡改会话响应。

     *
     * @param requestUri 请求 URI
     * @param status 状态
     * @param headers 表头或消息头集合
     * @param body 请求或响应体
     * @param managedSession 受管 WebSocket 会话
     */
    public ZlmWebRtcResponse {
        headers = HttpHeaders.readOnlyHttpHeaders(new HttpHeaders(headers));
        body = body == null ? new byte[0] : body.clone();
    }

    /**
     * 返回 SDP 或错误响应体的副本。
     *
     * @return 不可影响内部状态的字节数组
     */
    @Override
    public byte[] body() {
        return body.clone();
    }
}

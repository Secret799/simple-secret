package com.ss.easymedia.webrtc.domain;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

/**
 * Honeybee 对外 WebRTC 信令响应。
 *
 * @param status 返回给 WHIP/WHEP 客户端的状态码
 * @param headers 可公开的响应头
 * @param body SDP Answer 或错误响应体
 */
public record WebRtcGatewayResponse(HttpStatusCode status, HttpHeaders headers, byte[] body) {

    /**
     * 防御性复制可变响应数据。
     */
    public WebRtcGatewayResponse {
        headers = HttpHeaders.readOnlyHttpHeaders(new HttpHeaders(headers));
        body = body == null ? new byte[0] : body.clone();
    }

    /**
     * 返回响应体副本。
     *
     * @return 不可修改内部状态的字节数组
     */
    @Override
    public byte[] body() {
        return body.clone();
    }
}

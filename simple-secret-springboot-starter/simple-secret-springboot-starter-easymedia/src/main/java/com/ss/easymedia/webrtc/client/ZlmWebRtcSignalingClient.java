package com.ss.easymedia.webrtc.client;

import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.domain.ZlmWebRtcResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.net.URI;

/**
 * ZLM 原生 WHIP/WHEP 信令客户端。
 */
public interface ZlmWebRtcSignalingClient {

    /**
     * 创建 WHIP 或 WHEP 信令会话。
     *
     * @return SDP Answer 或上游错误响应

     *
     * @param type 目标类型
     * @param app 媒体应用名
     * @param stream 媒体流标识
     * @param requestHeaders 允许转发的请求头
     * @param body 请求或响应体
     */
    ZlmWebRtcResponse create(WebRtcSessionType type, String app, String stream,
                             HttpHeaders requestHeaders, byte[] body);

    /**
     * 对既有受管会话执行 PATCH 或 DELETE 操作。
     *
     * @return 上游操作响应

     *
     * @param upstreamLocation 上游会话 Location
     * @param method HTTP 方法
     * @param requestHeaders 允许转发的请求头
     * @param body 请求或响应体
     */
    ZlmWebRtcResponse exchange(URI upstreamLocation, HttpMethod method,
                               HttpHeaders requestHeaders, byte[] body);
}

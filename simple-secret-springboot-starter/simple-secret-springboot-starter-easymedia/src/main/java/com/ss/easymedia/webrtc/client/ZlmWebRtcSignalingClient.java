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
     */
    ZlmWebRtcResponse create(WebRtcSessionType type, String app, String stream,
                             HttpHeaders requestHeaders, byte[] body);

    /**
     * 对既有受管会话执行 PATCH 或 DELETE 操作。
     *
     * @return 上游操作响应
     */
    ZlmWebRtcResponse exchange(URI upstreamLocation, HttpMethod method,
                               HttpHeaders requestHeaders, byte[] body);
}

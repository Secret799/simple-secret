package com.ss.easymedia.webrtc.service;

import com.ss.easymedia.webrtc.domain.WebRtcGatewayResponse;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import org.springframework.http.HttpHeaders;

/**
 * WebRTC 会话生命周期服务。
 */
public interface WebRtcSessionService {

    /**
     * 创建 WHIP 或 WHEP 会话。
     *
     * @return 可直接转换为 HTTP 响应的 SDP Answer
     */
    WebRtcGatewayResponse create(WebRtcSessionType type, String app, String stream,
                                 HttpHeaders requestHeaders, byte[] offerSdp, String clientIp);

    /**
     * 向受管会话转发 Trickle ICE SDP Fragment。
     *
     * @return 上游会话更新响应
     */
    WebRtcGatewayResponse patch(String sessionId, HttpHeaders requestHeaders,
                                byte[] sdpFragment, String clientIp);

    /**
     * 请求关闭指定会话，必要时交由后台重试。
     */
    void delete(String sessionId, String clientIp);

    /**
     * 执行后台调度的一次会话删除补偿。
     */
    void retryDelete(String sessionId);
}

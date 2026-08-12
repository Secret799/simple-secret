package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcOperation;

/**
 * WebRTC 信令限流器。
 */
public interface WebRtcRateLimiter {

    /**
     * 校验并消耗一次操作许可；超限时抛出对应的会话异常。

     *
     * @param operation 操作类型
     * @param identity 调用方身份
     * @param clientIp 客户端 IP 地址
     */
    void check(WebRtcOperation operation, WebRtcIdentity identity, String clientIp);
}

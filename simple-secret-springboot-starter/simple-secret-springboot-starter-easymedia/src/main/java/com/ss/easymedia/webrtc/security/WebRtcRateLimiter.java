package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcOperation;

/**
 * WebRTC 信令限流器。
 */
public interface WebRtcRateLimiter {

    /**
     * 校验并消耗一次操作许可；超限时抛出对应的会话异常。
     */
    void check(WebRtcOperation operation, WebRtcIdentity identity, String clientIp);
}

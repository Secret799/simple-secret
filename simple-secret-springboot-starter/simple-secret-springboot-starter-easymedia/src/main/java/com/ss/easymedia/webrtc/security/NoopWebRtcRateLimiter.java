package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcOperation;

/**
 * 未配置 RedissonClient 时使用的空限流器，始终放行。
 */
public class NoopWebRtcRateLimiter implements WebRtcRateLimiter {

    /** 不执行任何限流检查。 */
    @Override
    public void check(WebRtcOperation operation, WebRtcIdentity identity, String clientIp) {
    }
}

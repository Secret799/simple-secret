package com.ss.easymedia.webrtc.domain;

/**
 * WebRTC 请求身份。
 *
 * @param tenantId     租户标识
 * @param subject      主体标识
 * @param authenticated 是否已认证
 */
public record WebRtcIdentity(String tenantId, String subject, boolean authenticated) {

    /**
     * 生成租户隔离的限流主体键。
     *
     * @return 可用于 Redis 限流键的稳定主体标识
     */
    public String rateLimitKey() {
        return tenantId + ":" + subject;
    }
}

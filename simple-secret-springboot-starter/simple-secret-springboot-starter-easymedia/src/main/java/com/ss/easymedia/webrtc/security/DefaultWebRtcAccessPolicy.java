package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcSessionRecord;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;

import java.util.Objects;

/**
 * 默认 WebRTC 访问策略：要求认证并校验会话所有权。
 */
public class DefaultWebRtcAccessPolicy implements WebRtcAccessPolicy {

    /** 决定是否要求创建者已完成认证。 */
    private final WebRtcProperties properties;

    /**
     * 创建默认访问策略。
     */
    public DefaultWebRtcAccessPolicy(WebRtcProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验创建 WHIP 或 WHEP 会话的认证要求。
     */
    @Override
    public void authorizeCreate(WebRtcIdentity identity, WebRtcSessionType type,
                                String app, String stream) {
        requireAuthenticatedWhenConfigured(identity);
    }

    /**
     * 校验操作主体与会话创建者属于同一租户且身份一致。
     */
    @Override
    public void authorizeSession(WebRtcIdentity identity, WebRtcSessionRecord record) {
        requireAuthenticatedWhenConfigured(identity);
        if (!Objects.equals(identity.tenantId(), record.getTenantId())
                || !Objects.equals(identity.subject(), record.getSubject())) {
            throw WebRtcSessionException.forbidden("WEBRTC_SESSION_FORBIDDEN");
        }
    }

    /**
     * 在开启认证要求时拒绝匿名请求。
     */
    private void requireAuthenticatedWhenConfigured(WebRtcIdentity identity) {
        if (properties.getSecurity().isAuthenticationRequired() && !identity.authenticated()) {
            throw WebRtcSessionException.unauthorized("WEBRTC_AUTH_REQUIRED");
        }
    }
}

package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import com.ss.easymedia.webrtc.support.Sha256Utils;

import java.util.Objects;

/**
 * 默认 WebRTC 身份提供器。
 * <p>
 * 不依赖任何认证框架：配置了 {@code security.default-subject} 时，所有请求以此固定身份认证；
 * 未配置且开启认证要求时拒绝匿名请求；否则按客户端 IP 构造匿名身份。
 * 需要对接业务鉴权体系时，注册自定义 {@link WebRtcIdentityProvider} 替换本实现。
 */
public class DefaultWebRtcIdentityProvider implements WebRtcIdentityProvider {

    /** 提供认证开关和默认身份配置。 */
    private final WebRtcProperties properties;

    /**
     * 创建默认身份解析器。
     */
    public DefaultWebRtcIdentityProvider(WebRtcProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析当前请求身份：优先使用配置的固定主体，其次按配置拒绝或构造匿名身份。
     *
     * @return 绑定租户、主体和认证状态的请求身份
     */
    @Override
    public WebRtcIdentity current(String clientIp) {
        String configuredSubject = properties.getSecurity().getDefaultSubject();
        if (configuredSubject != null && !configuredSubject.isBlank()) {
            return new WebRtcIdentity(
                    Objects.requireNonNullElse(properties.getSecurity().getDefaultTenantId(), "000000"),
                    configuredSubject,
                    true);
        }
        if (properties.getSecurity().isAuthenticationRequired()) {
            throw WebRtcSessionException.unauthorized("WEBRTC_AUTH_REQUIRED");
        }
        String normalizedIp = Objects.requireNonNullElse(clientIp, "unknown");
        String subject = "anonymous:" + Sha256Utils.sha256Hex(normalizedIp).substring(0, 32);
        return new WebRtcIdentity("anonymous", subject, false);
    }
}

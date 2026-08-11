package com.ss.idempotent.key;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Objects;

/** 使用指定请求头、现有 session 或远端地址解析身份。 */
public final class DefaultRequestIdentityResolver implements RequestIdentityResolver {

    private final String identityHeader;

    /**
     * 创建 resolver。
     *
     * @param identityHeader 优先读取的可信身份请求头
     */
    public DefaultRequestIdentityResolver(String identityHeader) {
        String value = Objects.requireNonNull(identityHeader, "identityHeader").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Identity header must not be blank.");
        }
        this.identityHeader = value;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request");
        String headerValue = request.getHeader(identityHeader);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue.trim();
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            return "session:" + session.getId();
        }
        String remoteAddress = request.getRemoteAddr();
        return "remote:" + (remoteAddress == null ? "unknown" : remoteAddress);
    }
}

package com.ss.netty.message;

import com.ss.netty.auth.NettyWebSocketPrincipal;

import java.util.Objects;
import java.util.Optional;

/** Netty WebSocket 入站文本消息的不可变上下文。 */
public final class NettyWebSocketMessage {

    private final String path;
    private final String sessionId;
    private final NettyWebSocketPrincipal principal;
    private final String payload;

    /** 创建并校验消息上下文。 */
    public NettyWebSocketMessage(String path, String sessionId,
                                 NettyWebSocketPrincipal principal, String payload) {
        this.path = requirePath(path);
        this.sessionId = requireText(sessionId, "sessionId");
        this.principal = principal;
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
    }

    /** 返回端点路径。 */
    public String path() {
        return path;
    }

    /** 返回当前 Netty channel 的长连接标识。 */
    public String sessionId() {
        return sessionId;
    }

    /** 返回认证身份；匿名端点为空。 */
    public Optional<NettyWebSocketPrincipal> principal() {
        return Optional.ofNullable(principal);
    }

    /** 返回完整文本载荷。 */
    public String payload() {
        return payload;
    }

    private static String requirePath(String value) {
        String path = requireText(value, "path");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        return path;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

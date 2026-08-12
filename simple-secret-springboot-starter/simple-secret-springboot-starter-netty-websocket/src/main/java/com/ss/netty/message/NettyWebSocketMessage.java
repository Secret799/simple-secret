package com.ss.netty.message;

import com.ss.netty.auth.NettyWebSocketPrincipal;

import java.util.Objects;
import java.util.Optional;

/** Netty WebSocket 入站文本消息的不可变上下文。 */
public final class NettyWebSocketMessage {

    /**
     * 文件或资源路径。
     */
    private final String path;
    /**
     * 会话 ID。
     */
    private final String sessionId;
    /**
     * 认证主体。
     */
    private final NettyWebSocketPrincipal principal;
    /**
     * 消息负载。
     */
    private final String payload;

    /**
     * 创建并校验消息上下文。
     *
     * @param path 文件或资源路径
     * @param sessionId 会话 ID
     * @param principal 认证主体
     * @param payload 消息负载
     */
    public NettyWebSocketMessage(String path, String sessionId,
                                 NettyWebSocketPrincipal principal, String payload) {
        this.path = requirePath(path);
        this.sessionId = requireText(sessionId, "sessionId");
        this.principal = principal;
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
    }

    /**
     * 返回端点路径。
     *
     * @return 返回的 {@code String} 结果
     */
    public String path() {
        return path;
    }

    /**
     * 返回当前 Netty channel 的长连接标识。
     *
     * @return 返回的 {@code String} 结果
     */
    public String sessionId() {
        return sessionId;
    }

    /**
     * 返回认证身份；匿名端点为空。
     *
     * @return 返回的 {@code Optional<NettyWebSocketPrincipal>} 结果
     */
    public Optional<NettyWebSocketPrincipal> principal() {
        return Optional.ofNullable(principal);
    }

    /**
     * 返回完整文本载荷。
     *
     * @return 返回的 {@code String} 结果
     */
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

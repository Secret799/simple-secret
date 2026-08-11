package com.ss.netty.server;

import java.util.Objects;
import java.util.Set;

/**
 * 已校验的不可变 WebSocket 端点。
 *
 * @param name 配置名称
 * @param path 绝对路径
 * @param authenticationRequired 是否要求认证
 * @param allowedOrigins 精确允许的 Origin；空集合表示默认同源策略
 */
public record NettyWebSocketEndpoint(String name, String path,
                                     boolean authenticationRequired,
                                     Set<String> allowedOrigins) {

    /** 创建不可变端点快照。 */
    public NettyWebSocketEndpoint {
        name = requireText(name, "name");
        path = requireText(path, "path");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        allowedOrigins = Set.copyOf(Objects.requireNonNull(
                allowedOrigins, "allowedOrigins must not be null"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

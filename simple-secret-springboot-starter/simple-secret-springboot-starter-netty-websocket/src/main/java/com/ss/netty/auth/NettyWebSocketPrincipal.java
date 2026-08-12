package com.ss.netty.auth;

import java.util.Map;
import java.util.Objects;

/**
 * 认证成功的不可变 WebSocket 身份。
 *
 * @param sessionKey 用于聚合同一身份多条连接的稳定键
 * @param name 展示名称
 * @param attributes 应用自定义只读属性
 */
public record NettyWebSocketPrincipal(String sessionKey, String name,
                                      Map<String, Object> attributes) {

    /**
     * 创建并校验身份。
     *
     * @param sessionKey 会话主体键
     * @param name 名称
     * @param attributes 扩展属性
     */
    public NettyWebSocketPrincipal {
        sessionKey = requireText(sessionKey, "sessionKey");
        name = requireText(name, "name");
        attributes = Map.copyOf(Objects.requireNonNull(
                attributes, "attributes must not be null"));
    }

    /** 返回不包含自定义属性的安全身份摘要。 */
    @Override
    public String toString() {
        return "NettyWebSocketPrincipal[sessionKey=" + sessionKey + ", name=" + name + ']';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

package com.ss.websocket.session;

import java.util.Map;

/**
 * WebSocket 握手认证后的不可变身份。
 *
 * @param sessionKey 会话分组键，例如用户 ID
 * @param name 可读名称，可为 {@code null}
 * @param attributes 应用附加属性
 */
public record WebSocketPrincipal(String sessionKey, String name, Map<String, Object> attributes) {

    /** 创建并校验不可变身份。 */
    public WebSocketPrincipal {
        sessionKey = requireText(sessionKey, "sessionKey");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

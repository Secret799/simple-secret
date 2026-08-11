package com.ss.websocket.broker;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在应用节点之间传递的不可变 WebSocket 文本消息。
 *
 * @param sourceNodeId 来源节点标识
 * @param path WebSocket 端点路径
 * @param sessionKeys 目标会话键；空集合表示广播
 * @param message 文本正文
 */
public record WebSocketBrokerMessage(String sourceNodeId, String path, Set<String> sessionKeys,
                                     String message) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建并校验跨节点消息。 */
    public WebSocketBrokerMessage {
        sourceNodeId = requireText(sourceNodeId, "sourceNodeId");
        path = requirePath(path);
        sessionKeys = sessionKeys == null ? Set.of() : sessionKeys.stream()
                .map(key -> requireText(key, "sessionKey"))
                .collect(Collectors.toUnmodifiableSet());
        if (message == null) {
            throw new NullPointerException("message must not be null");
        }
    }

    /** 判断该消息是否是指定路径的广播。 */
    public boolean broadcast() {
        return sessionKeys.isEmpty();
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

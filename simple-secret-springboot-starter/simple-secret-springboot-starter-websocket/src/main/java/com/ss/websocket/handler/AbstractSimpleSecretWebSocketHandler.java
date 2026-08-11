package com.ss.websocket.handler;

import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/** 带固定端点路径的 Simple Secret WebSocket 处理器基类。 */
public abstract class AbstractSimpleSecretWebSocketHandler extends AbstractWebSocketHandler {

    private final String path;

    /** 创建处理指定端点路径的处理器。 */
    protected AbstractSimpleSecretWebSocketHandler(String path) {
        if (path == null || path.isBlank() || !path.trim().startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        this.path = path.trim();
    }

    /** 获取处理器注册路径。 */
    public final String path() {
        return path;
    }
}

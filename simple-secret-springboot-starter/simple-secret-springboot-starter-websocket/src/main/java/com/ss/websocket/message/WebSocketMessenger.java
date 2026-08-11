package com.ss.websocket.message;

import com.ss.websocket.session.WebSocketSessionRegistry;

import java.util.Objects;

/** 当前应用实例内的 WebSocket 文本消息服务。 */
public final class WebSocketMessenger {

    private final WebSocketSessionRegistry registry;
    private final WebSocketMessageSender sender;

    /** 创建本地消息服务。 */
    public WebSocketMessenger(WebSocketSessionRegistry registry, WebSocketMessageSender sender) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.sender = Objects.requireNonNull(sender, "sender must not be null");
    }

    /**
     * 向指定路径和会话键下的全部本地连接发送文本。
     *
     * @return 成功发送的连接数量
     */
    public int send(String path, String sessionKey, String message) {
        return Math.toIntExact(registry.sessions(path, sessionKey).stream()
                .filter(session -> sender.sendText(session, message))
                .count());
    }

    /**
     * 向指定路径下的全部本地连接广播文本。
     *
     * @return 成功发送的连接数量
     */
    public int broadcast(String path, String message) {
        return Math.toIntExact(registry.sessions(path).stream()
                .filter(session -> sender.sendText(session, message))
                .count());
    }
}

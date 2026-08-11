package com.ss.websocket.message;

import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Objects;

/** 向单个 WebSocket 会话安全发送消息。 */
public final class WebSocketMessageSender {

    /** 向打开的会话发送文本。 */
    public boolean sendText(WebSocketSession session, String message) {
        Objects.requireNonNull(message, "message must not be null");
        return send(session, new TextMessage(message));
    }

    /** 向打开的会话发送 Pong。 */
    public boolean sendPong(WebSocketSession session) {
        return send(session, new PongMessage());
    }

    private static boolean send(WebSocketSession session, WebSocketMessage<?> message) {
        WebSocketSession requiredSession = Objects.requireNonNull(session, "session must not be null");
        if (!requiredSession.isOpen()) {
            return false;
        }
        try {
            requiredSession.sendMessage(message);
            return true;
        } catch (IOException exception) {
            throw new WebSocketDeliveryException(requiredSession.getId(), exception);
        }
    }
}

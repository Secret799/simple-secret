package com.ss.websocket.message;

/** WebSocket 消息无法写入连接时抛出的异常。 */
public final class WebSocketDeliveryException extends RuntimeException {

    /** 创建不包含消息正文的发送异常。 */
    public WebSocketDeliveryException(String sessionId, Throwable cause) {
        super("Failed to send WebSocket message to session " + sessionId, cause);
    }
}

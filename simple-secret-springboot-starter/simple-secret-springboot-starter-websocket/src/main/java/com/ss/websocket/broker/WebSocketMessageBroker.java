package com.ss.websocket.broker;

import java.util.function.Consumer;

/** 应用侧跨节点 WebSocket 消息传输扩展点。 */
public interface WebSocketMessageBroker {

    /** 发布一条跨节点消息。 */
    void publish(WebSocketBrokerMessage message);

    /**
     * 订阅跨节点消息。
     *
     * @return 用于精确注销当前订阅的句柄
     */
    AutoCloseable subscribe(Consumer<WebSocketBrokerMessage> consumer);
}

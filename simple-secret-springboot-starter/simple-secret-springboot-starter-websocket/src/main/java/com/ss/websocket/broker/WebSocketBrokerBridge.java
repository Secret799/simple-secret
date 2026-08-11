package com.ss.websocket.broker;

import com.ss.websocket.message.WebSocketMessenger;

import java.util.Objects;
import java.util.Set;

/** 将本地 WebSocket 投递与应用提供的跨节点 Broker 连接起来。 */
public final class WebSocketBrokerBridge implements AutoCloseable {

    private final String nodeId;
    private final WebSocketMessenger messenger;
    private final WebSocketMessageBroker broker;
    private final AutoCloseable subscription;
    private boolean closed;

    /** 创建桥接器并立即订阅 Broker。 */
    public WebSocketBrokerBridge(String nodeId, WebSocketMessenger messenger,
                                 WebSocketMessageBroker broker) {
        this.nodeId = requireText(nodeId, "nodeId");
        this.messenger = Objects.requireNonNull(messenger, "messenger must not be null");
        this.broker = Objects.requireNonNull(broker, "broker must not be null");
        this.subscription = Objects.requireNonNull(
                broker.subscribe(this::receive), "broker subscription must not be null");
    }

    /** 本地投递后向其他节点发布定向文本。 */
    public int send(String path, String sessionKey, String message) {
        int delivered = messenger.send(path, sessionKey, message);
        broker.publish(new WebSocketBrokerMessage(nodeId, path, Set.of(sessionKey), message));
        return delivered;
    }

    /** 本地投递后向其他节点发布广播文本。 */
    public int broadcast(String path, String message) {
        int delivered = messenger.broadcast(path, message);
        broker.publish(new WebSocketBrokerMessage(nodeId, path, Set.of(), message));
        return delivered;
    }

    /** 精确注销桥接器创建的 Broker 订阅。 */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            subscription.close();
            closed = true;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to close WebSocket broker subscription", exception);
        }
    }

    private void receive(WebSocketBrokerMessage message) {
        if (nodeId.equals(message.sourceNodeId())) {
            return;
        }
        if (message.broadcast()) {
            messenger.broadcast(message.path(), message.message());
            return;
        }
        message.sessionKeys().forEach(sessionKey ->
                messenger.send(message.path(), sessionKey, message.message()));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

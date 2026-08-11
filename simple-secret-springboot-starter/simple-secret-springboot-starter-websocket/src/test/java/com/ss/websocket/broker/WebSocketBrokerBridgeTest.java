package com.ss.websocket.broker;

import com.ss.websocket.message.WebSocketMessageSender;
import com.ss.websocket.message.WebSocketMessenger;
import com.ss.websocket.session.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证跨节点桥接的本地投递、来源去重和关闭行为。 */
class WebSocketBrokerBridgeTest {

    private final WebSocketSessionRegistry registry =
            new WebSocketSessionRegistry(10_000, 512 * 1024);
    private final WebSocketMessenger messenger =
            new WebSocketMessenger(registry, new WebSocketMessageSender());
    private final TestBroker broker = new TestBroker();
    private final WebSocketBrokerBridge bridge =
            new WebSocketBrokerBridge("node-a", messenger, broker);

    @Test
    void shouldDeliverLocallyThenPublishCompleteTargetMessage() throws Exception {
        WebSocketSession local = register("/events", "42", "session-1");

        int delivered = bridge.send("/events", "42", "hello");

        assertThat(delivered).isEqualTo(1);
        verify(local).sendMessage(argThat(text("hello")));
        assertThat(broker.published).containsExactly(new WebSocketBrokerMessage(
                "node-a", "/events", Set.of("42"), "hello"));
    }

    @Test
    void shouldIgnoreOwnMessageAndDeliverRemoteTargetAndBroadcast() throws Exception {
        WebSocketSession user = register("/events", "42", "session-1");
        WebSocketSession other = register("/events", "7", "session-2");

        broker.receive(new WebSocketBrokerMessage(
                "node-a", "/events", Set.of(), "own"));
        verify(user, never()).sendMessage(argThat(text("own")));

        broker.receive(new WebSocketBrokerMessage(
                "node-b", "/events", Set.of("42"), "targeted"));
        verify(user).sendMessage(argThat(text("targeted")));
        verify(other, never()).sendMessage(argThat(text("targeted")));

        broker.receive(new WebSocketBrokerMessage(
                "node-b", "/events", Set.of(), "broadcast"));
        verify(user).sendMessage(argThat(text("broadcast")));
        verify(other).sendMessage(argThat(text("broadcast")));
    }

    @Test
    void shouldCloseSubscriptionExactlyOnce() {
        bridge.close();
        bridge.close();

        assertThat(broker.closeCount).isEqualTo(1);
    }

    private WebSocketSession register(String path, String key, String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        registry.register(path, key, session);
        return session;
    }

    private static org.mockito.ArgumentMatcher<org.springframework.web.socket.WebSocketMessage<?>>
            text(String payload) {
        return message -> message instanceof TextMessage text && payload.equals(text.getPayload());
    }

    private static final class TestBroker implements WebSocketMessageBroker {
        private final List<WebSocketBrokerMessage> published = new ArrayList<>();
        private Consumer<WebSocketBrokerMessage> consumer;
        private int closeCount;

        @Override
        public void publish(WebSocketBrokerMessage message) {
            published.add(message);
        }

        @Override
        public AutoCloseable subscribe(Consumer<WebSocketBrokerMessage> consumer) {
            this.consumer = consumer;
            return () -> closeCount++;
        }

        private void receive(WebSocketBrokerMessage message) {
            consumer.accept(message);
        }
    }
}

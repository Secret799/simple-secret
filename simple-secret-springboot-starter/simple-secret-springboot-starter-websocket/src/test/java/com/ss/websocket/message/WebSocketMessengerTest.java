package com.ss.websocket.message;

import com.ss.websocket.session.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证按会话键发送和按路径广播。 */
class WebSocketMessengerTest {

    private final WebSocketSessionRegistry registry =
            new WebSocketSessionRegistry(10_000, 512 * 1024);
    private final WebSocketMessenger messenger =
            new WebSocketMessenger(registry, new WebSocketMessageSender());

    @Test
    void shouldSendToEveryConnectionForSameSessionKey() throws Exception {
        WebSocketSession first = register("/events", "42", "session-1", true);
        WebSocketSession second = register("/events", "42", "session-2", true);
        register("/events", "7", "session-3", true);

        int delivered = messenger.send("/events", "42", "hello");

        assertThat(delivered).isEqualTo(2);
        verify(first).sendMessage(argThat(message ->
                message instanceof TextMessage text && "hello".equals(text.getPayload())));
        verify(second).sendMessage(argThat(message ->
                message instanceof TextMessage text && "hello".equals(text.getPayload())));
    }

    @Test
    void shouldBroadcastOnlyToRequestedPathAndCountOpenSessions() throws Exception {
        WebSocketSession open = register("/events", "42", "session-1", true);
        WebSocketSession closed = register("/events", "7", "session-2", false);
        WebSocketSession otherPath = register("/alerts", "42", "session-3", true);

        int delivered = messenger.broadcast("/events", "notice");

        assertThat(delivered).isEqualTo(1);
        verify(open).sendMessage(argThat(message ->
                message instanceof TextMessage text && "notice".equals(text.getPayload())));
        verify(closed, never()).sendMessage(org.mockito.ArgumentMatchers.any());
        verify(otherPath, never()).sendMessage(org.mockito.ArgumentMatchers.any());
    }

    private WebSocketSession register(String path, String key, String id, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(open);
        registry.register(path, key, session);
        return session;
    }
}

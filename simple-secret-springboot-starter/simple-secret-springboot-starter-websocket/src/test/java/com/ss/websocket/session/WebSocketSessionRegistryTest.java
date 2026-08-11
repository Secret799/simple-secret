package com.ss.websocket.session;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证本机会话注册表的多连接和精确清理行为。 */
class WebSocketSessionRegistryTest {

    private final WebSocketSessionRegistry registry =
            new WebSocketSessionRegistry(10_000, 512 * 1024);

    @Test
    void shouldKeepMultipleSessionsForSameKeyAndRemoveOnlyClosedSession() {
        WebSocketSession first = session("session-1");
        WebSocketSession second = session("session-2");

        WebSocketSession registeredFirst = registry.register("/events", "42", first);
        WebSocketSession registeredSecond = registry.register("/events", "42", second);

        assertThat(registeredFirst).isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
        assertThat(registeredSecond).isInstanceOf(ConcurrentWebSocketSessionDecorator.class);
        assertThat(registry.sessions("/events", "42"))
                .extracting(WebSocketSession::getId)
                .containsExactlyInAnyOrder("session-1", "session-2");

        assertThat(registry.remove("/events", "42", "session-1")).isTrue();
        assertThat(registry.sessions("/events", "42"))
                .extracting(WebSocketSession::getId)
                .containsExactly("session-2");
        assertThat(registry.remove("/events", "42", "missing")).isFalse();
    }

    @Test
    void shouldRemoveEmptyBucketsAndReturnImmutableViews() {
        registry.register("/events", "42", session("session-1"));

        List<WebSocketSession> sessions = registry.sessions("/events", "42");
        assertThatThrownBy(() -> sessions.clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(registry.snapshot()).containsEntry("/events", 1);

        registry.remove("/events", "42", "session-1");

        assertThat(registry.sessions("/events", "42")).isEmpty();
        assertThat(registry.sessions("/events")).isEmpty();
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldRejectInvalidRegistrationArguments() {
        WebSocketSession session = session("session-1");

        assertThatThrownBy(() -> registry.register("events", "42", session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
        assertThatThrownBy(() -> registry.register("/events", " ", session))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionKey");
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }
}

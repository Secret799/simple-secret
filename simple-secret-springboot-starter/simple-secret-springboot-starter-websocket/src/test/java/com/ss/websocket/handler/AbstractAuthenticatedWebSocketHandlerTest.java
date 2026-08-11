package com.ss.websocket.handler;

import com.ss.websocket.auth.WebSocketAuthenticationInterceptor;
import com.ss.websocket.session.WebSocketPrincipal;
import com.ss.websocket.session.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证认证 handler 的会话生命周期。 */
class AbstractAuthenticatedWebSocketHandlerTest {

    private final WebSocketSessionRegistry registry =
            new WebSocketSessionRegistry(10_000, 512 * 1024);
    private final TestHandler handler = new TestHandler(registry);

    @Test
    void shouldRegisterAndRemoveExactAuthenticatedSession() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketAuthenticationInterceptor.PRINCIPAL_ATTRIBUTE,
                new WebSocketPrincipal("42", "alice", Map.of()));
        WebSocketSession session = session("session-1", attributes);

        handler.afterConnectionEstablished(session);

        assertThat(handler.path()).isEqualTo("/events");
        assertThat(handler.currentPrincipal(session).sessionKey()).isEqualTo("42");
        assertThat(registry.sessions("/events", "42"))
                .extracting(WebSocketSession::getId)
                .containsExactly("session-1");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(registry.sessions("/events", "42")).isEmpty();
    }

    @Test
    void shouldCloseSessionWhenPrincipalIsMissing() throws Exception {
        WebSocketSession session = session("session-1", new HashMap<>());

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldRollbackRegistrationWhenConnectionHookFails() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketAuthenticationInterceptor.PRINCIPAL_ATTRIBUTE,
                new WebSocketPrincipal("42", "alice", Map.of()));
        WebSocketSession session = session("session-1", attributes);
        AbstractAuthenticatedWebSocketHandler failing =
                new AbstractAuthenticatedWebSocketHandler("/events", registry) {
                    @Override
                    protected void onConnectionEstablished(WebSocketSession current,
                                                           WebSocketPrincipal principal) {
                        throw new IllegalStateException("hook failed");
                    }
                };

        assertThatThrownBy(() -> failing.afterConnectionEstablished(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("hook failed");
        assertThat(registry.sessions("/events", "42")).isEmpty();
    }

    private static WebSocketSession session(String id, Map<String, Object> attributes) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    private static final class TestHandler extends AbstractAuthenticatedWebSocketHandler {

        private TestHandler(WebSocketSessionRegistry registry) {
            super("/events", registry);
        }

        private WebSocketPrincipal currentPrincipal(WebSocketSession session) {
            return principal(session);
        }
    }
}

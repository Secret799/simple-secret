package com.ss.websocket.auth;

import com.ss.websocket.session.WebSocketPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 验证认证握手拦截器的接受、拒绝和失败关闭行为。 */
class WebSocketAuthenticationInterceptorTest {

    private final ServerHttpRequest request = mock(ServerHttpRequest.class);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    @Test
    void shouldStoreAuthenticatedPrincipalInSessionAttributes() {
        WebSocketPrincipal principal = new WebSocketPrincipal("42", "alice", Map.of());
        WebSocketAuthenticationInterceptor interceptor =
                new WebSocketAuthenticationInterceptor(ignored -> Optional.of(principal));
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(
                WebSocketAuthenticationInterceptor.PRINCIPAL_ATTRIBUTE, principal);
    }

    @Test
    void shouldRejectMissingOrFailedAuthenticationWithoutLeakingException() {
        WebSocketAuthenticationInterceptor missing =
                new WebSocketAuthenticationInterceptor(ignored -> Optional.empty());
        WebSocketAuthenticationInterceptor failed =
                new WebSocketAuthenticationInterceptor(ignored -> {
                    throw new IllegalStateException("token=secret");
                });

        assertThat(missing.beforeHandshake(
                request, response, handler, new HashMap<>())).isFalse();
        assertThat(failed.beforeHandshake(
                request, response, handler, new HashMap<>())).isFalse();
    }
}

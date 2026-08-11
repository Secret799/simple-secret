package com.ss.netty.message;

import com.ss.netty.auth.NettyWebSocketPrincipal;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NettyWebSocketMessageTest {

    @Test
    void shouldExposeTextMessageContext() {
        NettyWebSocketPrincipal principal = new NettyWebSocketPrincipal(
                "user-42", "Alice", Map.of());

        NettyWebSocketMessage message = new NettyWebSocketMessage(
                "/events", "session-1", principal, "hello");

        assertThat(message.path()).isEqualTo("/events");
        assertThat(message.sessionId()).isEqualTo("session-1");
        assertThat(message.principal()).contains(principal);
        assertThat(message.payload()).isEqualTo("hello");
    }

    @Test
    void shouldRejectNullPayload() {
        assertThatThrownBy(() -> new NettyWebSocketMessage(
                "/events", "session-1", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payload");
    }
}

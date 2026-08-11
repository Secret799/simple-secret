package com.ss.websocket.message;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证单连接发送的关闭和异常语义。 */
class WebSocketMessageSenderTest {

    private final WebSocketMessageSender sender = new WebSocketMessageSender();

    @Test
    void shouldSendTextAndPongToOpenSession() throws Exception {
        WebSocketSession session = session(true);
        ArgumentCaptor<WebSocketMessage<?>> messages =
                ArgumentCaptor.forClass(WebSocketMessage.class);

        assertThat(sender.sendText(session, "hello")).isTrue();
        assertThat(sender.sendPong(session)).isTrue();

        verify(session, org.mockito.Mockito.times(2)).sendMessage(messages.capture());
        assertThat(messages.getAllValues().get(0)).isInstanceOf(TextMessage.class);
        assertThat(((TextMessage) messages.getAllValues().get(0)).getPayload()).isEqualTo("hello");
        assertThat(messages.getAllValues().get(1)).isInstanceOf(PongMessage.class);
    }

    @Test
    void shouldReturnFalseWithoutSendingWhenSessionIsClosed() throws Exception {
        WebSocketSession session = session(false);

        assertThat(sender.sendText(session, "hello")).isFalse();

        verify(session, never()).sendMessage(any());
    }

    @Test
    void shouldWrapIoFailureWithoutIncludingPayload() throws Exception {
        WebSocketSession session = session(true);
        doThrow(new IOException("wire failed"))
                .when(session).sendMessage(any(WebSocketMessage.class));

        assertThatThrownBy(() -> sender.sendText(session, "password=secret"))
                .isInstanceOf(WebSocketDeliveryException.class)
                .hasMessageContaining("session-1")
                .hasMessageNotContaining("password=secret")
                .hasCauseInstanceOf(IOException.class);
    }

    private static WebSocketSession session(boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(open);
        return session;
    }
}

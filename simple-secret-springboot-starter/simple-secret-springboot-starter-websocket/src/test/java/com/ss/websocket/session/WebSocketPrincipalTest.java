package com.ss.websocket.session;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证握手身份的不可变性和输入约束。 */
class WebSocketPrincipalTest {

    @Test
    void shouldRejectBlankSessionKey() {
        assertThatThrownBy(() -> new WebSocketPrincipal(" ", "user", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionKey");
    }

    @Test
    void shouldDefensivelyCopyAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("tenant", "alpha");

        WebSocketPrincipal principal = new WebSocketPrincipal("42", "alice", attributes);
        attributes.put("tenant", "beta");

        assertThat(principal.sessionKey()).isEqualTo("42");
        assertThat(principal.attributes()).containsEntry("tenant", "alpha");
        assertThatThrownBy(() -> principal.attributes().put("role", "admin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

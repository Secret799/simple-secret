package com.ss.netty.auth;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NettyWebSocketPrincipalTest {

    @Test
    void shouldExposeValidatedImmutableIdentity() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("tenantId", 7L);

        NettyWebSocketPrincipal principal = new NettyWebSocketPrincipal(
                " user-42 ", " Alice ", attributes);
        attributes.put("tenantId", 8L);

        assertThat(principal.sessionKey()).isEqualTo("user-42");
        assertThat(principal.name()).isEqualTo("Alice");
        assertThat(principal.attributes()).containsEntry("tenantId", 7L);
        assertThat(principal.toString())
                .contains("user-42", "Alice")
                .doesNotContain("tenantId", "7");
        assertThatThrownBy(() -> principal.attributes().put("role", "admin"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectBlankSessionKey() {
        assertThatThrownBy(() -> new NettyWebSocketPrincipal(" ", "Alice", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionKey");
    }
}

package com.ss.netty.auth;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NettyWebSocketHandshakeRequestTest {

    @Test
    void shouldCopySensitiveRequestDataAndKeepToStringSafe() {
        List<String> authorization = new ArrayList<>(List.of("Bearer header-secret"));
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Authorization", authorization);
        Map<String, List<String>> query = new LinkedHashMap<>();
        query.put("token", new ArrayList<>(List.of("query-secret")));
        Map<String, String> cookies = new LinkedHashMap<>();
        cookies.put("sid", "cookie-secret");

        NettyWebSocketHandshakeRequest request = new NettyWebSocketHandshakeRequest(
                "GET", "/events?token=query-secret", "/events", headers, query, cookies,
                new InetSocketAddress("127.0.0.1", 18080));
        authorization.set(0, "changed");
        query.get("token").set(0, "changed");
        cookies.put("sid", "changed");

        assertThat(request.firstHeader("authorization")).contains("Bearer header-secret");
        assertThat(request.firstHeader("AUTHORIZATION")).contains("Bearer header-secret");
        assertThat(request.firstQueryParameter("token")).contains("query-secret");
        assertThat(request.cookie("sid")).contains("cookie-secret");
        assertThat(request.toString())
                .contains("GET", "/events", "127.0.0.1")
                .doesNotContain("header-secret", "query-secret", "cookie-secret");
        assertThatThrownBy(() -> request.headers().put("x", List.of("value")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectInvalidRequiredFields() {
        assertThatThrownBy(() -> new NettyWebSocketHandshakeRequest(
                " ", "/events", "/events", Map.of(), Map.of(), Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
        assertThatThrownBy(() -> new NettyWebSocketHandshakeRequest(
                "GET", "/events", "events", Map.of(), Map.of(), Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }
}

package com.ss.netty.server;

import com.ss.netty.auth.NettyWebSocketHandshakeRequest;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class NettyWebSocketRequestSnapshotsTest {

    @Test
    void shouldCopyHeadersQueryCookiesBeforeNettyRequestIsReleased() {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET,
                "/events?token=query-secret&tag=a&tag=b");
        request.headers().add("Authorization", "Bearer header-secret");
        request.headers().add("X-Tag", "first");
        request.headers().add("X-Tag", "second");
        request.headers().add(HttpHeaderNames.COOKIE,
                "sid=cookie-secret; theme=dark");

        NettyWebSocketHandshakeRequest snapshot = NettyWebSocketRequestSnapshots.from(
                request, new InetSocketAddress("127.0.0.1", 18080));
        request.release();

        assertThat(snapshot.path()).isEqualTo("/events");
        assertThat(snapshot.firstHeader("authorization")).contains("Bearer header-secret");
        assertThat(snapshot.headers().get("x-tag")).containsExactly("first", "second");
        assertThat(snapshot.queryParameters().get("tag")).containsExactly("a", "b");
        assertThat(snapshot.cookie("sid")).contains("cookie-secret");
        assertThat(snapshot.toString())
                .doesNotContain("header-secret", "query-secret", "cookie-secret");
    }
}

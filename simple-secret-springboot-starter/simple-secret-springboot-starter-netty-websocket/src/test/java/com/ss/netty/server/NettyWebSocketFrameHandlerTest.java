package com.ss.netty.server;

import com.ss.netty.auth.NettyWebSocketPrincipal;
import com.ss.netty.config.NettyWebSocketProperties;
import com.ss.netty.handler.NettyWebSocketMessageHandler;
import com.ss.netty.message.NettyWebSocketMessage;
import com.ss.netty.session.NettyWebSocketChannelRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NettyWebSocketFrameHandlerTest {

    @Test
    void shouldRejectUnknownPathAndCrossOriginWithoutLeakingRequestData() {
        NettyWebSocketEndpointRegistry endpoints = endpoints("/events", false, List.of(), List.of());
        EmbeddedChannel unknown = channel(endpoints, null, Runnable::run);
        FullHttpRequest unknownRequest = upgrade("/missing?token=secret", "http://localhost:9839");

        assertThat(unknown.writeInbound(unknownRequest)).isFalse();
        assertStatus(unknown, HttpResponseStatus.NOT_FOUND);

        EmbeddedChannel crossOrigin = channel(endpoints, null, Runnable::run);
        FullHttpRequest crossOriginRequest = upgrade("/events", "https://attacker.example");
        assertThat(crossOrigin.writeInbound(crossOriginRequest)).isFalse();
        assertStatus(crossOrigin, HttpResponseStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectMissingOrFailedAuthentication() {
        NettyWebSocketEndpointRegistry endpoints = endpoints(
                "/private", true, List.of(), List.of(), request -> Optional.empty());
        EmbeddedChannel channel = channel(endpoints, request -> Optional.empty(), Runnable::run);

        assertThat(channel.writeInbound(upgrade("/private", "http://localhost:9839"))).isFalse();
        assertStatus(channel, HttpResponseStatus.UNAUTHORIZED);
    }

    @Test
    void shouldDispatchTextWithCopiedContextAndCloseUnsupportedBinary() {
        AtomicReference<NettyWebSocketMessage> received = new AtomicReference<>();
        NettyWebSocketMessageHandler handler = new NettyWebSocketMessageHandler() {
            @Override
            public String path() {
                return "/events";
            }

            @Override
            public void handle(NettyWebSocketMessage message) {
                received.set(message);
            }
        };
        NettyWebSocketEndpointRegistry endpoints = endpoints(
                "/events", false, List.of(), List.of(handler));
        EmbeddedChannel channel = channel(endpoints, null, Runnable::run);
        NettyWebSocketPrincipal principal = new NettyWebSocketPrincipal(
                "user-42", "Alice", Map.of());
        channel.attr(NettyWebSocketFrameHandler.PATH_ATTRIBUTE).set("/events");
        channel.attr(NettyWebSocketFrameHandler.PRINCIPAL_ATTRIBUTE).set(principal);

        assertThat(channel.writeInbound(new TextWebSocketFrame("hello"))).isFalse();
        assertThat(received.get().path()).isEqualTo("/events");
        assertThat(received.get().sessionId()).isEqualTo(channel.id().asLongText());
        assertThat(received.get().principal()).contains(principal);
        assertThat(received.get().payload()).isEqualTo("hello");

        assertThat(channel.writeInbound(new BinaryWebSocketFrame())).isFalse();
        assertThat(channel.isActive()).isFalse();
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldAcceptPongControlFrameWithoutClosingConnection() {
        NettyWebSocketEndpointRegistry endpoints = endpoints(
                "/events", false, List.of(), List.of());
        EmbeddedChannel channel = channel(endpoints, null, Runnable::run);
        channel.attr(NettyWebSocketFrameHandler.PATH_ATTRIBUTE).set("/events");
        PongWebSocketFrame pong = new PongWebSocketFrame(
                Unpooled.buffer().writeByte(7));

        assertThat(channel.writeInbound(pong)).isFalse();

        assertThat(channel.isActive()).isTrue();
        assertThat(pong.refCnt()).isZero();
        Object outbound = channel.readOutbound();
        assertThat(outbound).isNull();
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldRetainPingPayloadOnlyUntilPongWriteAndReleaseCloseFrame() {
        NettyWebSocketEndpointRegistry endpoints = endpoints(
                "/events", false, List.of(), List.of());
        EmbeddedChannel pingChannel = channel(endpoints, null, Runnable::run);
        PingWebSocketFrame ping = new PingWebSocketFrame(
                Unpooled.buffer().writeByte(9));

        assertThat(pingChannel.writeInbound(ping)).isFalse();
        PongWebSocketFrame pong = pingChannel.readOutbound();
        assertThat(pong.content().readByte()).isEqualTo((byte) 9);
        assertThat(ping.refCnt()).isEqualTo(1);
        pong.release();
        assertThat(ping.refCnt()).isZero();
        pingChannel.finishAndReleaseAll();

        EmbeddedChannel closeChannel = channel(endpoints, null, Runnable::run);
        CloseWebSocketFrame close = new CloseWebSocketFrame();
        assertThat(closeChannel.writeInbound(close)).isFalse();
        assertThat(closeChannel.isActive()).isFalse();
        assertThat(close.refCnt()).isZero();
        closeChannel.finishAndReleaseAll();
    }

    @Test
    void shouldCloseIdleConnectionWhenCompleteHandshakeDeadlineExpires() {
        NettyWebSocketEndpointRegistry endpoints = endpoints(
                "/events", false, List.of(), List.of());
        EmbeddedChannel channel = channel(
                endpoints, null, Runnable::run, Duration.ofMillis(10));

        channel.advanceTimeBy(11, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();

        assertThat(channel.isActive()).isFalse();
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldAuthenticateOutsideIoThreadAndKeepOriginalHandshakeDeadline() {
        AtomicBoolean authenticated = new AtomicBoolean();
        AtomicReference<Runnable> queuedAuthentication = new AtomicReference<>();
        NettyWebSocketPrincipal principal = new NettyWebSocketPrincipal(
                "user-42", "Alice", Map.of());
        com.ss.netty.auth.NettyWebSocketAuthenticator authenticator = request -> {
            authenticated.set(true);
            return Optional.of(principal);
        };
        NettyWebSocketEndpointRegistry endpoints = endpoints(
                "/private", true, List.of(), List.of(), authenticator);
        EmbeddedChannel channel = channel(
                endpoints, authenticator, queuedAuthentication::set, Duration.ofMillis(10));

        assertThat(channel.writeInbound(
                upgrade("/private", "http://localhost:9839"))).isFalse();
        assertThat(authenticated).isFalse();
        assertThat(queuedAuthentication.get()).isNotNull();

        channel.advanceTimeBy(11, TimeUnit.MILLISECONDS);
        channel.runScheduledPendingTasks();
        queuedAuthentication.get().run();
        channel.runPendingTasks();

        assertThat(channel.isActive()).isFalse();
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldSubmitMessagesFromSameConnectionInOrder() {
        ArrayDeque<Runnable> submitted = new ArrayDeque<>();
        List<String> received = new ArrayList<>();
        NettyWebSocketMessageHandler handler = new NettyWebSocketMessageHandler() {
            @Override
            public String path() {
                return "/events";
            }

            @Override
            public void handle(NettyWebSocketMessage message) {
                received.add(message.payload());
            }
        };
        NettyWebSocketEndpointRegistry endpoints = endpoints(
                "/events", false, List.of(), List.of(handler));
        EmbeddedChannel channel = new EmbeddedChannel(new NettyWebSocketFrameHandler(
                endpoints, null, submitted::addLast, new NettyWebSocketChannelRegistry(),
                65_536, Duration.ofSeconds(10), new Semaphore(8)));
        channel.attr(NettyWebSocketFrameHandler.PATH_ATTRIBUTE).set("/events");

        channel.writeInbound(new TextWebSocketFrame("first"));
        channel.writeInbound(new TextWebSocketFrame("second"));

        assertThat(submitted).hasSize(1);
        submitted.removeFirst().run();
        assertThat(received).containsExactly("first");
        assertThat(submitted).hasSize(1);
        submitted.removeFirst().run();
        assertThat(received).containsExactly("first", "second");
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldCompleteAuthenticatedHandshakeRegisterAndDispatchText() {
        AtomicReference<NettyWebSocketMessage> received = new AtomicReference<>();
        NettyWebSocketMessageHandler handler = new NettyWebSocketMessageHandler() {
            @Override
            public String path() {
                return "/private";
            }

            @Override
            public void handle(NettyWebSocketMessage message) {
                received.set(message);
            }
        };
        NettyWebSocketPrincipal principal = new NettyWebSocketPrincipal(
                "user-42", "Alice", Map.of());
        com.ss.netty.auth.NettyWebSocketAuthenticator authenticator =
                request -> Optional.of(principal);
        NettyWebSocketEndpointRegistry endpoints = endpoints(
                "/private", true, List.of(), List.of(handler), authenticator);
        NettyWebSocketChannelRegistry channels = new NettyWebSocketChannelRegistry();
        EmbeddedChannel channel = new EmbeddedChannel(
                new HttpServerCodec(),
                new HttpObjectAggregator(65_536),
                new WebSocketFrameAggregator(65_536),
                new NettyWebSocketFrameHandler(
                        endpoints, authenticator, Runnable::run, channels,
                        65_536, Duration.ofSeconds(10)));

        assertThat(channel.writeInbound(
                upgrade("/private", "http://localhost:9839"))).isFalse();
        channel.runPendingTasks();
        ByteBuf response = channel.readOutbound();
        try {
            assertThat(response.toString(java.nio.charset.StandardCharsets.US_ASCII))
                    .startsWith("HTTP/1.1 101");
        } finally {
            response.release();
        }
        assertThat(channels.countByPrincipal("/private", "user-42")).isEqualTo(1);

        assertThat(channel.writeInbound(new TextWebSocketFrame("hello"))).isFalse();
        assertThat(received.get()).isNotNull();
        assertThat(received.get().principal()).contains(principal);
        assertThat(received.get().payload()).isEqualTo("hello");
        channel.finishAndReleaseAll();
    }

    private static NettyWebSocketEndpointRegistry endpoints(
            String path, boolean authenticationRequired, List<String> origins,
            List<NettyWebSocketMessageHandler> handlers) {
        return endpoints(path, authenticationRequired, origins, handlers, null);
    }

    private static NettyWebSocketEndpointRegistry endpoints(
            String path, boolean authenticationRequired, List<String> origins,
            List<NettyWebSocketMessageHandler> handlers,
            com.ss.netty.auth.NettyWebSocketAuthenticator authenticator) {
        NettyWebSocketProperties properties = new NettyWebSocketProperties();
        NettyWebSocketProperties.Endpoint endpoint = new NettyWebSocketProperties.Endpoint();
        endpoint.setPath(path);
        endpoint.setAuthenticationRequired(authenticationRequired);
        endpoint.setAllowedOrigins(origins);
        properties.getEndpoints().put("test", endpoint);
        return new NettyWebSocketEndpointRegistry(properties, handlers, authenticator);
    }

    private static EmbeddedChannel channel(
            NettyWebSocketEndpointRegistry endpoints,
            com.ss.netty.auth.NettyWebSocketAuthenticator authenticator,
            java.util.concurrent.Executor executor) {
        return channel(endpoints, authenticator, executor, Duration.ofSeconds(10));
    }

    private static EmbeddedChannel channel(
            NettyWebSocketEndpointRegistry endpoints,
            com.ss.netty.auth.NettyWebSocketAuthenticator authenticator,
            java.util.concurrent.Executor executor,
            Duration handshakeTimeout) {
        return new EmbeddedChannel(new NettyWebSocketFrameHandler(
                endpoints, authenticator, executor, new NettyWebSocketChannelRegistry(),
                65_536, handshakeTimeout));
    }

    private static FullHttpRequest upgrade(String uri, String origin) {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        request.headers().set(HttpHeaderNames.HOST, "localhost:9839");
        request.headers().set(HttpHeaderNames.ORIGIN, origin);
        request.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
        request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13");
        request.headers().set(HttpHeaderNames.SEC_WEBSOCKET_KEY,
                "dGhlIHNhbXBsZSBub25jZQ==");
        return request;
    }

    private static void assertStatus(EmbeddedChannel channel, HttpResponseStatus expected) {
        FullHttpResponse response = channel.readOutbound();
        try {
            assertThat(response.status()).isEqualTo(expected);
            assertThat(response.content().toString(java.nio.charset.StandardCharsets.UTF_8))
                    .doesNotContain("secret");
        } finally {
            response.release();
            channel.finishAndReleaseAll();
        }
    }
}

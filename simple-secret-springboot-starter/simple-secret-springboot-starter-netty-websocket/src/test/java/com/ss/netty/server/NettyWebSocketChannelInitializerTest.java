package com.ss.netty.server;

import com.ss.netty.config.NettyWebSocketProperties;
import com.ss.netty.handler.NettyWebSocketMessageHandler;
import com.ss.netty.message.NettyWebSocketMessage;
import com.ss.netty.session.NettyWebSocketChannelRegistry;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NettyWebSocketChannelInitializerTest {

    @Test
    void shouldInstallOnlyRequiredBoundedProtocolHandlers() {
        NettyWebSocketProperties properties = anonymousProperties();
        NettyWebSocketEndpointRegistry endpoints = new NettyWebSocketEndpointRegistry(
                properties, List.of(), null);
        NettyWebSocketChannelInitializer initializer = new NettyWebSocketChannelInitializer(
                properties, endpoints, null, Runnable::run,
                new NettyWebSocketChannelRegistry());

        EmbeddedChannel channel = new EmbeddedChannel(initializer);

        assertThat(channel.pipeline().get(HttpServerCodec.class)).isNotNull();
        HttpObjectAggregator httpAggregator = channel.pipeline().get(HttpObjectAggregator.class);
        assertThat(httpAggregator).isNotNull();
        assertThat(channel.pipeline().get(WebSocketFrameAggregator.class)).isNotNull();
        assertThat(channel.pipeline().get(NettyWebSocketFrameHandler.class)).isNotNull();
        channel.finishAndReleaseAll();
    }

    @Test
    void shouldAllowMaximumRunningHandlersPlusQueuedMessages() throws InterruptedException {
        NettyWebSocketProperties properties = anonymousProperties();
        properties.setHandlerCoreSize(1);
        properties.setHandlerMaxSize(3);
        properties.setHandlerQueueCapacity(2);
        CountDownLatch handlersStarted = new CountDownLatch(3);
        CountDownLatch releaseHandlers = new CountDownLatch(1);
        NettyWebSocketMessageHandler handler = new NettyWebSocketMessageHandler() {
            @Override
            public String path() {
                return "/events";
            }

            @Override
            public void handle(NettyWebSocketMessage message) {
                handlersStarted.countDown();
                try {
                    releaseHandlers.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        NettyWebSocketEndpointRegistry endpoints = new NettyWebSocketEndpointRegistry(
                properties, List.of(handler), null);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 3, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.AbortPolicy());
        NettyWebSocketChannelInitializer initializer = new NettyWebSocketChannelInitializer(
                properties, endpoints, null, executor, new NettyWebSocketChannelRegistry());
        List<EmbeddedChannel> channels = new ArrayList<>();

        try {
            for (int index = 0; index < 6; index++) {
                EmbeddedChannel channel = new EmbeddedChannel(initializer);
                channel.attr(NettyWebSocketFrameHandler.PATH_ATTRIBUTE).set("/events");
                channels.add(channel);
            }

            for (int index = 0; index < 5; index++) {
                assertThat(channels.get(index).writeInbound(
                        new TextWebSocketFrame("message-" + index))).isFalse();
            }

            assertThat(handlersStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.getLargestPoolSize()).isEqualTo(3);
            assertThat(channels.subList(0, 5)).allMatch(EmbeddedChannel::isActive);

            assertThat(channels.get(5).writeInbound(
                    new TextWebSocketFrame("overflow"))).isFalse();
            assertThat(channels.get(5).isActive()).isFalse();
        } finally {
            releaseHandlers.countDown();
            executor.shutdownNow();
            for (EmbeddedChannel channel : channels) {
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    void shouldBoundSameConnectionBacklogAndReuseReleasedCapacity()
            throws InterruptedException {
        NettyWebSocketProperties properties = anonymousProperties();
        properties.setHandlerCoreSize(1);
        properties.setHandlerMaxSize(3);
        properties.setHandlerQueueCapacity(2);
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        NettyWebSocketMessageHandler handler = new NettyWebSocketMessageHandler() {
            @Override
            public String path() {
                return "/events";
            }

            @Override
            public void handle(NettyWebSocketMessage message) {
                handlerStarted.countDown();
                try {
                    releaseHandler.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        NettyWebSocketEndpointRegistry endpoints = new NettyWebSocketEndpointRegistry(
                properties, List.of(handler), null);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 3, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2),
                new ThreadPoolExecutor.AbortPolicy());
        NettyWebSocketChannelInitializer initializer = new NettyWebSocketChannelInitializer(
                properties, endpoints, null, executor, new NettyWebSocketChannelRegistry());
        EmbeddedChannel overloaded = new EmbeddedChannel(initializer);
        EmbeddedChannel replacement = new EmbeddedChannel(initializer);
        overloaded.attr(NettyWebSocketFrameHandler.PATH_ATTRIBUTE).set("/events");
        replacement.attr(NettyWebSocketFrameHandler.PATH_ATTRIBUTE).set("/events");

        try {
            for (int index = 0; index < 5; index++) {
                assertThat(overloaded.writeInbound(
                        new TextWebSocketFrame("message-" + index))).isFalse();
            }
            assertThat(handlerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(overloaded.isActive()).isTrue();

            assertThat(overloaded.writeInbound(new TextWebSocketFrame("overflow"))).isFalse();
            assertThat(overloaded.isActive()).isFalse();

            assertThat(replacement.writeInbound(new TextWebSocketFrame("replacement"))).isFalse();
            assertThat(replacement.isActive()).isTrue();
        } finally {
            releaseHandler.countDown();
            executor.shutdownNow();
            overloaded.finishAndReleaseAll();
            replacement.finishAndReleaseAll();
        }
    }

    static NettyWebSocketProperties anonymousProperties() {
        NettyWebSocketProperties properties = new NettyWebSocketProperties();
        NettyWebSocketProperties.Endpoint endpoint = new NettyWebSocketProperties.Endpoint();
        endpoint.setPath("/events");
        endpoint.setAuthenticationRequired(false);
        properties.getEndpoints().put("events", endpoint);
        return properties;
    }
}

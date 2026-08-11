package com.ss.netty.server;

import com.ss.netty.config.NettyWebSocketProperties;
import com.ss.netty.session.NettyWebSocketChannelRegistry;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.DefaultChannelId;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NettyWebSocketServerTest {

    @Test
    void shouldRequestRandomLoopbackBindAndStopIdempotently() {
        NettyWebSocketProperties properties = NettyWebSocketChannelInitializerTest
                .anonymousProperties();
        NettyWebSocketEndpointRegistry endpoints = new NettyWebSocketEndpointRegistry(
                properties, List.of(), null);
        NettyWebSocketChannelInitializer initializer = new NettyWebSocketChannelInitializer(
                properties, endpoints, null, Runnable::run,
                new NettyWebSocketChannelRegistry());
        AtomicReference<InetSocketAddress> requestedAddress = new AtomicReference<>();
        EmbeddedChannel boundChannel = new EmbeddedChannel(DefaultChannelId.newInstance());
        NettyWebSocketServer server = new NettyWebSocketServer(
                properties, initializer, (bootstrap, address) -> {
                    requestedAddress.set(address);
                    return boundChannel;
                });

        try {
            server.start();
            server.start();

            assertThat(server.isRunning()).isTrue();
            assertThat(requestedAddress.get().getPort()).isZero();
            InetAddress address = requestedAddress.get().getAddress();
            assertThat(address.isLoopbackAddress()).isTrue();
        } finally {
            server.stop();
            server.stop();
        }

        assertThat(server.isRunning()).isFalse();
        assertThat(boundChannel.isActive()).isFalse();
    }

    @Test
    void shouldShutdownStaleEventLoopsBeforeRestarting() {
        NettyWebSocketProperties properties = NettyWebSocketChannelInitializerTest
                .anonymousProperties();
        NettyWebSocketEndpointRegistry endpoints = new NettyWebSocketEndpointRegistry(
                properties, List.of(), null);
        NettyWebSocketChannelInitializer initializer = new NettyWebSocketChannelInitializer(
                properties, endpoints, null, Runnable::run,
                new NettyWebSocketChannelRegistry());
        EmbeddedChannel first = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel second = new EmbeddedChannel(DefaultChannelId.newInstance());
        AtomicInteger bindCount = new AtomicInteger();
        List<EventLoopGroup> bossGroups = new ArrayList<>();
        List<EventLoopGroup> workerGroups = new ArrayList<>();
        NettyWebSocketServer server = new NettyWebSocketServer(
                properties, initializer, (bootstrap, address) -> {
                    bossGroups.add(bootstrap.config().group());
                    workerGroups.add(bootstrap.config().childGroup());
                    return bindCount.getAndIncrement() == 0 ? first : second;
                });

        try {
            server.start();
            first.close();
            first.runPendingTasks();
            server.start();

            assertThat(bindCount.get()).isEqualTo(2);
            assertThat(bossGroups.get(0).isShuttingDown()).isTrue();
            assertThat(workerGroups.get(0).isShuttingDown()).isTrue();
            assertThat(server.isRunning()).isTrue();
        } finally {
            server.stop();
            first.finishAndReleaseAll();
            second.finishAndReleaseAll();
        }
    }

    @Test
    void shouldCleanupBossWhenWorkerCreationFailsAndAllowRetry() {
        NettyWebSocketProperties properties = NettyWebSocketChannelInitializerTest
                .anonymousProperties();
        NettyWebSocketEndpointRegistry endpoints = new NettyWebSocketEndpointRegistry(
                properties, List.of(), null);
        NettyWebSocketChannelInitializer initializer = new NettyWebSocketChannelInitializer(
                properties, endpoints, null, Runnable::run,
                new NettyWebSocketChannelRegistry());
        AtomicInteger creationCount = new AtomicInteger();
        List<EventLoopGroup> groups = new ArrayList<>();
        NettyWebSocketEventLoopFactory factory = (threads, threadFactory) -> {
            if (creationCount.incrementAndGet() == 2) {
                throw new IllegalStateException("worker creation failed");
            }
            EventLoopGroup group = new NioEventLoopGroup(threads, threadFactory);
            groups.add(group);
            return group;
        };
        EmbeddedChannel boundChannel = new EmbeddedChannel(DefaultChannelId.newInstance());
        NettyWebSocketServer server = new NettyWebSocketServer(
                properties, initializer, (bootstrap, address) -> boundChannel, factory);

        assertThatThrownBy(server::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to start");
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).isShuttingDown()).isTrue();

        try {
            server.start();
            assertThat(server.isRunning()).isTrue();
        } finally {
            server.stop();
            boundChannel.finishAndReleaseAll();
        }
    }
}

package com.ss.netty.session;

import com.ss.netty.auth.NettyWebSocketPrincipal;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.DefaultChannelId;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.Channel;

import static org.assertj.core.api.Assertions.assertThat;

class NettyWebSocketChannelRegistryTest {

    private final List<EmbeddedChannel> channels = new ArrayList<>();

    @AfterEach
    void closeChannels() {
        channels.forEach(channel -> {
            channel.finishAndReleaseAll();
            channel.runPendingTasks();
        });
    }

    @Test
    void shouldKeepMultipleConnectionsAndRemoveOnlyClosedChannel() {
        NettyWebSocketChannelRegistry registry = new NettyWebSocketChannelRegistry();
        NettyWebSocketPrincipal principal = new NettyWebSocketPrincipal(
                "user-42", "Alice", Map.of());
        EmbeddedChannel first = channel();
        EmbeddedChannel second = channel();

        registry.register("/events", principal, first);
        registry.register("/events", principal, second);

        assertThat(registry.totalCount()).isEqualTo(2);
        assertThat(registry.countByPath("/events")).isEqualTo(2);
        assertThat(registry.countByPrincipal("/events", "user-42")).isEqualTo(2);

        assertThat(registry.remove("/events", principal, first)).isTrue();
        assertThat(registry.totalCount()).isEqualTo(1);
        assertThat(registry.countByPrincipal("/events", "user-42")).isEqualTo(1);
        assertThat(registry.remove("/events", principal, first)).isFalse();
    }

    @Test
    void shouldDeliverTextBySessionPathAndPrincipal() {
        NettyWebSocketChannelRegistry registry = new NettyWebSocketChannelRegistry();
        NettyWebSocketPrincipal principal = new NettyWebSocketPrincipal(
                "user-42", "Alice", Map.of());
        EmbeddedChannel first = channel();
        EmbeddedChannel second = channel();
        EmbeddedChannel otherPath = channel();
        registry.register("/events", principal, first);
        registry.register("/events", principal, second);
        registry.register("/alerts", principal, otherPath);

        assertThat(registry.sendToSession(first.id().asLongText(), "direct")).isTrue();
        assertFrame(first, "direct");
        assertThat(registry.sendToPath("/events", "broadcast")).isEqualTo(2);
        assertFrame(first, "broadcast");
        assertFrame(second, "broadcast");
        assertThat(registry.sendToPrincipal("/events", "user-42", "private")).isEqualTo(2);
        assertFrame(first, "private");
        assertFrame(second, "private");
        assertThat(registry.sendToPrincipalAllPaths("user-42", "all")).isEqualTo(3);
        assertFrame(first, "all");
        assertFrame(second, "all");
        assertFrame(otherPath, "all");
    }

    @Test
    void shouldAutomaticallyRemoveClosedAndAnonymousChannels() {
        NettyWebSocketChannelRegistry registry = new NettyWebSocketChannelRegistry();
        EmbeddedChannel channel = channel();
        registry.register("/public", null, channel);
        assertThat(registry.snapshot()).containsEntry("/public", 1);

        channel.close();
        channel.runPendingTasks();

        assertThat(registry.totalCount()).isZero();
        assertThat(registry.snapshot()).isEmpty();
        assertThat(registry.sendToSession(channel.id().asLongText(), "ignored")).isFalse();
    }

    @Test
    void shouldKeepPathIndexWhenLastOldConnectionIsRemovedDuringRegistration() throws Exception {
        NettyWebSocketChannelRegistry registry = new NettyWebSocketChannelRegistry();
        EmbeddedChannel oldChannel = channel();
        EmbeddedChannel newDelegate = channel();
        registry.register("/events", null, oldChannel);
        BlockingChannel newChannel = blockingChannel(newDelegate, 2);

        runConcurrentRegistrationAndRemoval(
                registry, null, oldChannel, newChannel.channel(), newChannel);

        assertThat(registry.countByPath("/events")).isEqualTo(1);
        assertThat(registry.sendToPath("/events", "retained")).isEqualTo(1);
        assertFrame(newDelegate, "retained");
    }

    @Test
    void shouldKeepPrincipalIndexWhenLastOldConnectionIsRemovedDuringRegistration()
            throws Exception {
        NettyWebSocketChannelRegistry registry = new NettyWebSocketChannelRegistry();
        NettyWebSocketPrincipal principal = new NettyWebSocketPrincipal(
                "user-42", "Alice", Map.of());
        EmbeddedChannel oldChannel = channel();
        EmbeddedChannel newDelegate = channel();
        registry.register("/events", principal, oldChannel);
        BlockingChannel newChannel = blockingChannel(newDelegate, 3);

        runConcurrentRegistrationAndRemoval(
                registry, principal, oldChannel, newChannel.channel(), newChannel);

        assertThat(registry.countByPrincipal("/events", "user-42")).isEqualTo(1);
        assertThat(registry.sendToPrincipal("/events", "user-42", "retained")).isEqualTo(1);
        assertFrame(newDelegate, "retained");
    }

    private EmbeddedChannel channel() {
        EmbeddedChannel channel = new EmbeddedChannel(DefaultChannelId.newInstance());
        channels.add(channel);
        return channel;
    }

    private static void runConcurrentRegistrationAndRemoval(
            NettyWebSocketChannelRegistry registry,
            NettyWebSocketPrincipal principal,
            Channel oldChannel,
            Channel newChannel,
            BlockingChannel blockingChannel) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> registration = executor.submit(
                    () -> registry.register("/events", principal, newChannel));
            assertThat(blockingChannel.blocked().await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch removalStarted = new CountDownLatch(1);
            Future<Boolean> removal = executor.submit(() -> {
                removalStarted.countDown();
                return registry.remove("/events", principal, oldChannel);
            });
            assertThat(removalStarted.await(5, TimeUnit.SECONDS)).isTrue();
            boolean removalCompleted = false;
            try {
                assertThat(removal.get(200, TimeUnit.MILLISECONDS)).isTrue();
                removalCompleted = true;
            } catch (TimeoutException expectedForAtomicRegistration) {
                // Atomic registration keeps removal inside compute blocked until add completes.
            }
            blockingChannel.resume().countDown();
            registration.get(5, TimeUnit.SECONDS);
            if (!removalCompleted) {
                assertThat(removal.get(5, TimeUnit.SECONDS)).isTrue();
            }
        } finally {
            blockingChannel.resume().countDown();
            executor.shutdownNow();
        }
    }

    private static BlockingChannel blockingChannel(
            EmbeddedChannel delegate, int blockedIdInvocation) {
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicInteger idInvocations = new AtomicInteger();
        Channel proxy = (Channel) Proxy.newProxyInstance(
                Channel.class.getClassLoader(), new Class<?>[]{Channel.class},
                (ignored, method, arguments) -> {
                    if ("id".equals(method.getName())
                            && idInvocations.incrementAndGet() == blockedIdInvocation) {
                        blocked.countDown();
                        if (!resume.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("timed out waiting to resume registration");
                        }
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
        return new BlockingChannel(proxy, blocked, resume);
    }

    private record BlockingChannel(
            Channel channel, CountDownLatch blocked, CountDownLatch resume) {
    }

    private static void assertFrame(EmbeddedChannel channel, String expected) {
        channel.runPendingTasks();
        channel.flushOutbound();
        TextWebSocketFrame frame = channel.readOutbound();
        try {
            assertThat(frame.text()).isEqualTo(expected);
        } finally {
            frame.release();
        }
    }
}

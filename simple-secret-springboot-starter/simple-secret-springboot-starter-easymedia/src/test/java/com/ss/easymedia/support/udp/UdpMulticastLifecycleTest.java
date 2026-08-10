package com.ss.easymedia.support.udp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UdpMulticastLifecycleTest {

    @Test
    void rejectsInvalidConstructionParameters() {
        assertThatThrownBy(() -> new UdpMulticastListener(
                "127.0.0.1", 12345, "127.0.0.1", packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multicast");
        assertThatThrownBy(() -> new UdpMulticastListener(
                "239.1.1.1", 0, "127.0.0.1", packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> new UdpMulticastListener(
                "239.1.1.1", 12345, "127.0.0.1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("messageHandler");
        assertThatThrownBy(() -> new UdpMulticastListener(
                "not-a-host.example", 0, "127.0.0.1", packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> new UdpMulticastListener(
                "not-a-host.example", 12345, "127.0.0.1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("messageHandler");
        assertThatThrownBy(() -> new UdpMulticastListener(
                "multicast.example", 12345, "127.0.0.1", packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric");
        assertThatThrownBy(() -> new UdpMulticastListener(
                "239.1.1.1", 12345, "192.0.2.1", packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("local network interface");
        assertThatThrownBy(() -> new UdpMulticastListener(
                "ff02::1", 12345, "127.0.0.1", packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("address family");
    }

    @Test
    void rejectsInvalidMaximumMessageLength() {
        UdpMulticastListener listener = new UdpMulticastListener(
                "239.1.1.1", 12345, "127.0.0.1", packet -> { });

        assertThatThrownBy(() -> listener.setMaxMessageLength(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("maxMessageLength");
        assertThatThrownBy(() -> listener.setMaxMessageLength(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessageLength");
        assertThatThrownBy(() -> listener.setMaxMessageLength(65_508))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessageLength");

        listener.setMaxMessageLength(1);
        listener.setMaxMessageLength(65_507);
    }

    @Test
    void rejectsMessageLengthChangesAfterListenerStarts() throws Exception {
        UdpMulticastListener listener = new NoOpListener();
        listener.start();
        listener.join(1_000);

        assertThatThrownBy(() -> listener.setMaxMessageLength(2048))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before");
    }

    @Test
    void closesSocketWhenLeavingMulticastGroupFails() throws Exception {
        UdpMulticastListener listener = new UdpMulticastListener(
                "239.1.1.1", 12345, "127.0.0.1", packet -> { });
        FailingLeaveSocket socket = new FailingLeaveSocket();
        setField(listener, "socket", socket);

        listener.stopListener();

        assertTrue(socket.isClosed());
    }

    @Test
    void managerWaitsForListenerThreadToExit() throws Exception {
        UdpMulticastManager manager = new UdpMulticastManager();
        DelayedStopListener listener = new DelayedStopListener();
        manager.joinGroup(listener);
        assertTrue(listener.started.await(1, TimeUnit.SECONDS));

        manager.leaveGroup(listener.getGroupIp(), listener.getPort(), listener.getLocalIp());

        assertFalse(listener.isAlive());
        assertTrue(listener.terminated.await(1, TimeUnit.SECONDS));
    }

    private static void setField(UdpMulticastListener listener, String name, Object value) throws Exception {
        Field field = UdpMulticastListener.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(listener, value);
    }

    private static final class FailingLeaveSocket extends MulticastSocket {
        private FailingLeaveSocket() throws IOException {
            super((SocketAddress) null);
        }

        @Override
        public void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf) throws IOException {
            throw new IOException("leave failed");
        }
    }

    private static final class DelayedStopListener extends UdpMulticastListener {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch stopRequested = new CountDownLatch(1);
        private final CountDownLatch terminated = new CountDownLatch(1);

        private DelayedStopListener() {
            super("239.1.1.1", 12345, "127.0.0.1", packet -> { });
        }

        @Override
        public void run() {
            started.countDown();
            try {
                stopRequested.await();
                Thread.sleep(150);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                terminated.countDown();
            }
        }

        @Override
        public void stopListener() {
            stopRequested.countDown();
        }
    }

    private static final class NoOpListener extends UdpMulticastListener {
        private NoOpListener() {
            super("239.1.1.1", 12345, "127.0.0.1", packet -> { });
        }

        @Override
        public void run() {
        }
    }
}

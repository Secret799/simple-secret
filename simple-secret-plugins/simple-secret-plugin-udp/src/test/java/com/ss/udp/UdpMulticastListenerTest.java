package com.ss.udp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UdpMulticastListenerTest {

    @Test
    void validatesAddressesPortAndHandler() {
        assertThatThrownBy(() -> new UdpMulticastListener(
                "127.0.0.1", 12345, "127.0.0.1", packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multicast");
        assertThatThrownBy(() -> new UdpMulticastListener(
                "239.1.1.1", 0, "127.0.0.1", packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
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
        assertThatThrownBy(() -> new UdpMulticastListener(
                "239.1.1.1", 12345, "127.0.0.1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("messageHandler");
    }

    @Test
    void validatesMaximumMessageLengthBeforeStart() throws InterruptedException {
        UdpMulticastListener listener = new NoOpMulticastListener();

        assertThatThrownBy(() -> listener.setMaxMessageLength(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessageLength");
        assertThatThrownBy(() -> listener.setMaxMessageLength(65_508))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessageLength");

        listener.setMaxMessageLength(65_507);
        assertThat(listener.getMaxMessageLength()).isEqualTo(65_507);

        listener.start();
        listener.join(1_000);
        assertThatThrownBy(() -> listener.setMaxMessageLength(2048))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before");
    }

    @Test
    void closesSocketEvenWhenLeavingGroupFails() throws Exception {
        UdpMulticastListener listener = new UdpMulticastListener(
                "239.1.1.1", 12345, "127.0.0.1", packet -> { });
        FailingLeaveSocket socket = new FailingLeaveSocket();
        setField(listener, "socket", socket);

        listener.stopListener();

        assertThat(socket.isClosed()).isTrue();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = UdpMulticastListener.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FailingLeaveSocket extends MulticastSocket {
        private FailingLeaveSocket() throws IOException {
            super((SocketAddress) null);
        }

        @Override
        public void leaveGroup(SocketAddress multicastAddress, NetworkInterface networkInterface)
                throws IOException {
            throw new IOException("leave failed");
        }
    }

    private static final class NoOpMulticastListener extends UdpMulticastListener {
        private NoOpMulticastListener() {
            super("239.1.1.1", 12345, "127.0.0.1", packet -> { });
        }

        @Override
        public void run() {
        }
    }
}

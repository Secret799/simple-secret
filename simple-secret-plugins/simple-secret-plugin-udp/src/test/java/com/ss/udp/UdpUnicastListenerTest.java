package com.ss.udp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UdpUnicastListenerTest {

    @Test
    void validatesBindAddressPortAndHandler() {
        assertThatThrownBy(() -> new UdpUnicastListener(
                "localhost", 12345, packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("numeric");
        assertThatThrownBy(() -> new UdpUnicastListener(
                "239.1.1.1", 12345, packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unicast");
        assertThatThrownBy(() -> new UdpUnicastListener(
                "127.0.0.1", 65_536, packet -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> new UdpUnicastListener(
                "127.0.0.1", 12345, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("messageHandler");
    }

    @Test
    void receivesIndependentPayloadSnapshotsAndStopsPromptly() throws Exception {
        CountDownLatch received = new CountDownLatch(2);
        List<DatagramPacket> packets = new CopyOnWriteArrayList<>();
        ScriptedDatagramSocket socket = new ScriptedDatagramSocket("first", "second");
        UdpUnicastListener listener = new TestUnicastListener(
                socket, packet -> {
                    packets.add(packet);
                    received.countDown();
                });
        listener.start();
        awaitRunning(listener);

        assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
        listener.stopListener();
        listener.join(1_000);

        assertThat(listener.isAlive()).isFalse();
        assertThat(payload(packets.get(0))).isEqualTo("first");
        assertThat(payload(packets.get(1))).isEqualTo("second");
        assertThat(packets.get(0).getData()).isNotSameAs(packets.get(1).getData());
    }

    @Test
    void validatesMaximumMessageLengthBeforeStart() throws InterruptedException {
        UdpUnicastListener listener = new NoOpUnicastListener();

        assertThatThrownBy(() -> listener.setMaxMessageLength(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("maxMessageLength");
        assertThatThrownBy(() -> listener.setMaxMessageLength(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxMessageLength");
        listener.setMaxMessageLength(65_507);

        listener.start();
        listener.join(1_000);
        assertThatThrownBy(() -> listener.setMaxMessageLength(2048))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before");
    }

    private static String payload(DatagramPacket packet) {
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
    }

    private static void awaitRunning(UdpUnicastListener listener) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!listener.isRunning() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(listener.isRunning()).isTrue();
    }

    private static final class TestUnicastListener extends UdpUnicastListener {
        private final DatagramSocket socket;

        private TestUnicastListener(DatagramSocket socket, UdpMessageHandler messageHandler) {
            super("127.0.0.1", 12345, messageHandler);
            this.socket = socket;
        }

        @Override
        DatagramSocket createSocket() {
            return socket;
        }
    }

    private static final class ScriptedDatagramSocket extends DatagramSocket {
        private final Deque<byte[]> payloads = new ArrayDeque<>();

        private ScriptedDatagramSocket(String... values) throws IOException {
            super((SocketAddress) null);
            Arrays.stream(values)
                    .map(value -> value.getBytes(StandardCharsets.UTF_8))
                    .forEach(payloads::addLast);
        }

        @Override
        public synchronized void receive(DatagramPacket packet) throws IOException {
            byte[] payload = payloads.pollFirst();
            if (payload == null) {
                throw new SocketTimeoutException("no scripted payload");
            }
            System.arraycopy(payload, 0, packet.getData(), packet.getOffset(), payload.length);
            packet.setLength(payload.length);
        }
    }

    private static final class NoOpUnicastListener extends UdpUnicastListener {
        private NoOpUnicastListener() {
            super("127.0.0.1", 12345, packet -> { });
        }

        @Override
        public void run() {
        }
    }
}

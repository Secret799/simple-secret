package com.ss.udp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UdpManagerTest {

    @Test
    void multicastManagerRejectsDuplicatesAndWaitsForStop() throws Exception {
        UdpMulticastManager manager = new UdpMulticastManager();
        ControlledMulticastListener first = new ControlledMulticastListener();
        ControlledMulticastListener duplicate = new ControlledMulticastListener();

        assertThat(manager.joinGroup(first)).isTrue();
        assertThat(manager.joinGroup(duplicate)).isFalse();
        assertThat(first.started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(duplicate.getState()).isEqualTo(Thread.State.NEW);
        assertThat(manager.getActiveGroupCount()).isEqualTo(1);

        assertThat(manager.leaveGroup(first.getGroupIp(), first.getPort(), first.getLocalIp())).isTrue();

        assertThat(first.isAlive()).isFalse();
        assertThat(manager.getActiveGroupCount()).isZero();
    }

    @Test
    void unicastManagerRollsBackWhenListenerCannotStart() throws Exception {
        UdpUnicastManager manager = new UdpUnicastManager();
        FinishedUnicastListener listener = new FinishedUnicastListener();
        listener.start();
        listener.join(1_000);

        assertThatThrownBy(() -> manager.startListener(listener))
                .isInstanceOf(IllegalThreadStateException.class);
        assertThat(manager.getActiveListenerCount()).isZero();
    }

    @Test
    void shutdownAllStopsEveryUnicastListener() throws Exception {
        UdpUnicastManager manager = new UdpUnicastManager();
        ControlledUnicastListener first = new ControlledUnicastListener("127.0.0.1", 12001);
        ControlledUnicastListener second = new ControlledUnicastListener("127.0.0.1", 12002);

        manager.startListener(first);
        manager.startListener(second);
        assertThat(first.started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(second.started.await(1, TimeUnit.SECONDS)).isTrue();

        manager.shutdownAll();

        assertThat(first.isAlive()).isFalse();
        assertThat(second.isAlive()).isFalse();
        assertThat(manager.getActiveListenerCount()).isZero();
    }

    private static final class ControlledMulticastListener extends UdpMulticastListener {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch stopRequested = new CountDownLatch(1);

        private ControlledMulticastListener() {
            super("239.1.1.1", 12345, "127.0.0.1", packet -> { });
        }

        @Override
        public void run() {
            started.countDown();
            awaitStop(stopRequested);
        }

        @Override
        public void stopListener() {
            stopRequested.countDown();
        }
    }

    private static final class ControlledUnicastListener extends UdpUnicastListener {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch stopRequested = new CountDownLatch(1);

        private ControlledUnicastListener(String bindIp, int port) {
            super(bindIp, port, packet -> { });
        }

        @Override
        public void run() {
            started.countDown();
            awaitStop(stopRequested);
        }

        @Override
        public void stopListener() {
            stopRequested.countDown();
        }
    }

    private static final class FinishedUnicastListener extends UdpUnicastListener {
        private FinishedUnicastListener() {
            super("127.0.0.1", 12003, packet -> { });
        }

        @Override
        public void run() {
        }
    }

    private static void awaitStop(CountDownLatch stopRequested) {
        try {
            stopRequested.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}

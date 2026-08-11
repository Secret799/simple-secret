package com.ss.consumer.udp;

import com.ss.udp.UdpMessageHandler;
import com.ss.udp.UdpMulticastListener;
import com.ss.udp.UdpMulticastManager;
import com.ss.udp.UdpUnicastListener;
import com.ss.udp.UdpUnicastManager;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the published UDP plugin from a third-party application classpath. */
class UdpPluginConsumerTest {

    @Test
    void exposesJdkOnlyListenerApi() {
        UdpMessageHandler handler = packet -> { };
        UdpUnicastListener unicast = new UdpUnicastListener("0.0.0.0", 19000, handler);
        UdpMulticastListener multicast =
                new UdpMulticastListener("239.1.1.1", 19001, "127.0.0.1", handler);

        unicast.setMaxMessageLength(4096);
        multicast.setMaxMessageLength(8192);

        assertThat(unicast.getBindIp()).isEqualTo("0.0.0.0");
        assertThat(unicast.getMaxMessageLength()).isEqualTo(4096);
        assertThat(multicast.getGroupIp()).isEqualTo("239.1.1.1");
        assertThat(multicast.getLocalIp()).isEqualTo("127.0.0.1");
        assertThat(multicast.getMaxMessageLength()).isEqualTo(8192);
    }

    @Test
    void managesListenerLifecycleWithoutSpring() throws Exception {
        UdpUnicastManager manager = new UdpUnicastManager();
        ControlledListener listener = new ControlledListener();

        assertThat(manager.startListener(listener)).isTrue();
        assertThat(listener.started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(manager.getActiveListenerCount()).isEqualTo(1);

        assertThat(manager.stopListener("127.0.0.1", 19002)).isTrue();
        assertThat(listener.isAlive()).isFalse();
        assertThat(manager.getActiveListenerCount()).isZero();

        assertThat(new UdpMulticastManager().getActiveGroupCount()).isZero();
    }

    private static final class ControlledListener extends UdpUnicastListener {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch stopped = new CountDownLatch(1);

        private ControlledListener() {
            super("127.0.0.1", 19002, packet -> { });
        }

        @Override
        public void run() {
            started.countDown();
            try {
                stopped.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void stopListener() {
            stopped.countDown();
        }
    }
}

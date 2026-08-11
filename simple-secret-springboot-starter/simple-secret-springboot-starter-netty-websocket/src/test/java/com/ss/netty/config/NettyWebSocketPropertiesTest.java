package com.ss.netty.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NettyWebSocketPropertiesTest {

    @Test
    void shouldUseClosedAndLoopbackSafeDefaults() {
        NettyWebSocketProperties properties = new NettyWebSocketProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isAutoStartup()).isTrue();
        assertThat(properties.getHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getPort()).isZero();
        assertThat(properties.getBossThreads()).isEqualTo(1);
        assertThat(properties.getWorkerThreads()).isZero();
        assertThat(properties.getMaxHttpContentLength()).isEqualTo(65_536);
        assertThat(properties.getMaxFramePayloadLength()).isEqualTo(65_536);
        assertThat(properties.getHandshakeTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getShutdownTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getHandlerCoreSize()).isEqualTo(2);
        assertThat(properties.getHandlerMaxSize()).isEqualTo(8);
        assertThat(properties.getHandlerQueueCapacity()).isEqualTo(1_024);
        assertThat(properties.getEndpoints()).isEmpty();
    }
}

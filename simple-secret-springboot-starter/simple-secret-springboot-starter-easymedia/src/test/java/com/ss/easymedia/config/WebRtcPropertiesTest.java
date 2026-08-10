package com.ss.easymedia.config;

import com.ss.easymedia.config.properties.WebRtcProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebRtcPropertiesTest {

    @Test
    void shouldBindProductionDefaultsAndOverrides() {
        Map<String, String> values = Map.of(
                "simple-secret.easymedia.webrtc.signaling-base-url", "http://127.0.0.1:17080",
                "simple-secret.easymedia.webrtc.session-ttl", "30m",
                "simple-secret.easymedia.webrtc.max-sdp-bytes", "32768",
                "simple-secret.easymedia.webrtc.trickle-ice-enabled", "true",
                "simple-secret.easymedia.webrtc.local-zlm-enabled", "true",
                "simple-secret.easymedia.webrtc.security.authentication-required", "false",
                "simple-secret.easymedia.webrtc.rate-limit.play-per-minute", "240"
        );
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(values);
        WebRtcProperties properties = new Binder(source)
                .bind("simple-secret.easymedia.webrtc", Bindable.of(WebRtcProperties.class))
                .orElseThrow(() -> new IllegalStateException("WebRTC properties not bound"));

        assertEquals(URI.create("http://127.0.0.1:17080"), properties.getSignalingBaseUrl());
        assertEquals(Duration.ofMinutes(30), properties.getSessionTtl());
        assertEquals(32768, properties.getMaxSdpBytes());
        org.junit.jupiter.api.Assertions.assertTrue(properties.isTrickleIceEnabled());
        org.junit.jupiter.api.Assertions.assertTrue(properties.isLocalZlmEnabled());
        assertFalse(properties.getSecurity().isAuthenticationRequired());
        assertEquals(240, properties.getRateLimit().getPlayPerMinute());
    }

    @Test
    void shouldDisableLocalZlmSignalingByDefault() {
        assertFalse(new WebRtcProperties().isLocalZlmEnabled());
    }
}

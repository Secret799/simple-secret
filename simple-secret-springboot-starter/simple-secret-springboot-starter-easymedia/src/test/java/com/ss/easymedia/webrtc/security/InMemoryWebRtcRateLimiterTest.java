package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import com.ss.easymedia.webrtc.repository.WebRtcRedisKeys;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryWebRtcRateLimiterTest {

    @Test
    void shouldEnforceConfiguredPerMinuteLimit() {
        WebRtcProperties properties = new WebRtcProperties();
        properties.getRateLimit().setPlayPerMinute(1);
        InMemoryWebRtcRateLimiter limiter = new InMemoryWebRtcRateLimiter(
                new WebRtcRedisKeys(), properties, Clock.systemUTC());
        WebRtcIdentity identity = new WebRtcIdentity("tenant-a", "user-1", true);

        limiter.check(WebRtcOperation.PLAY, identity, "203.0.113.10");

        assertThatThrownBy(() -> limiter.check(WebRtcOperation.PLAY, identity, "203.0.113.10"))
                .isInstanceOf(WebRtcSessionException.class)
                .extracting("errorCode")
                .isEqualTo("WEBRTC_RATE_LIMITED");
    }

    @Test
    void shouldFailClosedWhenLocalKeyCapacityIsExhausted() {
        WebRtcProperties properties = new WebRtcProperties();
        properties.getRateLimit().setLocalMaxKeys(1);
        InMemoryWebRtcRateLimiter limiter = new InMemoryWebRtcRateLimiter(
                new WebRtcRedisKeys(), properties, Clock.systemUTC());
        WebRtcIdentity identity = new WebRtcIdentity("tenant-a", "user-1", true);
        limiter.check(WebRtcOperation.PLAY, identity, "203.0.113.10");

        assertThatThrownBy(() -> limiter.check(WebRtcOperation.PLAY, identity, "203.0.113.11"))
                .isInstanceOf(WebRtcSessionException.class)
                .extracting("errorCode")
                .isEqualTo("WEBRTC_LOCAL_RATE_LIMIT_CAPACITY_EXCEEDED");
    }
}

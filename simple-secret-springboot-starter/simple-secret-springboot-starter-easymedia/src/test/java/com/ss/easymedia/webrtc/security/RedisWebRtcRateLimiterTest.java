package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import com.ss.easymedia.webrtc.repository.WebRtcRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisWebRtcRateLimiterTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RRateLimiter limiter;

    private WebRtcProperties properties;
    private WebRtcRedisKeys keys;
    private RedisWebRtcRateLimiter rateLimiter;
    private WebRtcIdentity identity;

    @BeforeEach
    void setUp() {
        properties = new WebRtcProperties();
        keys = new WebRtcRedisKeys();
        rateLimiter = new RedisWebRtcRateLimiter(redissonClient, keys, properties);
        identity = new WebRtcIdentity("tenant-a", "user-1", true);
    }

    @Test
    void shouldUsePlayRateAndExpireLimiterKey() {
        String key = keys.rateLimit(WebRtcOperation.PLAY, identity.rateLimitKey(), "10.0.0.8");
        when(redissonClient.getRateLimiter(key)).thenReturn(limiter);
        when(limiter.tryAcquire()).thenReturn(true);

        assertDoesNotThrow(() -> rateLimiter.check(
                WebRtcOperation.PLAY, identity, "10.0.0.8"));

        verify(limiter).trySetRate(RateType.OVERALL, 120, Duration.ofMinutes(1));
        verify(limiter).expire(Duration.ofMinutes(10));
        verify(limiter).tryAcquire();
    }

    @Test
    void shouldUseSessionOperationRateForPatchAndDelete() {
        when(redissonClient.getRateLimiter(org.mockito.ArgumentMatchers.anyString())).thenReturn(limiter);
        when(limiter.tryAcquire()).thenReturn(true);

        rateLimiter.check(WebRtcOperation.PATCH, identity, "10.0.0.8");
        rateLimiter.check(WebRtcOperation.DELETE, identity, "10.0.0.8");

        verify(limiter, org.mockito.Mockito.times(2))
                .trySetRate(RateType.OVERALL, 300, Duration.ofMinutes(1));
    }

    @Test
    void shouldRejectWhenPermitIsUnavailable() {
        String key = keys.rateLimit(WebRtcOperation.PUBLISH, identity.rateLimitKey(), "10.0.0.8");
        when(redissonClient.getRateLimiter(key)).thenReturn(limiter);
        when(limiter.tryAcquire()).thenReturn(false);

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> rateLimiter.check(WebRtcOperation.PUBLISH, identity, "10.0.0.8"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
        assertEquals("WEBRTC_RATE_LIMITED", error.getErrorCode());
    }

    @Test
    void shouldSkipRedisWhenRateLimitingIsDisabled() {
        properties.getRateLimit().setEnabled(false);

        rateLimiter.check(WebRtcOperation.PLAY, identity, "10.0.0.8");

        verifyNoInteractions(redissonClient);
        verify(limiter, never()).tryAcquire();
    }
}

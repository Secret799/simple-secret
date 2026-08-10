package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import com.ss.easymedia.webrtc.repository.WebRtcRedisKeys;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 有界的单机 WebRTC 固定窗口限流器。
 * <p>
 * 适用于未接入 Redis 的单实例部署；达到键容量上限时拒绝新维度，避免内存无限增长。
 */
public class InMemoryWebRtcRateLimiter implements WebRtcRateLimiter {

    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();

    private final WebRtcRedisKeys keys;
    private final WebRtcProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    /**
     * 创建单机限流器。
     *
     * @param keys       限流键生成器
     * @param properties WebRTC 配置
     * @param clock      时间源
     */
    public InMemoryWebRtcRateLimiter(WebRtcRedisKeys keys, WebRtcProperties properties, Clock clock) {
        this.keys = Objects.requireNonNull(keys, "keys");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized void check(WebRtcOperation operation, WebRtcIdentity identity, String clientIp) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }
        long now = clock.millis();
        removeExpired(now);
        String key = keys.rateLimit(operation, identity.rateLimitKey(), clientIp);
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= Math.max(1, properties.getRateLimit().getLocalMaxKeys())) {
                throw WebRtcSessionException.tooManyRequests(
                        "WEBRTC_LOCAL_RATE_LIMIT_CAPACITY_EXCEEDED");
            }
            window = new Window(now, expiresAt(now));
            windows.put(key, window);
        } else if (now - window.startedAt >= WINDOW_MILLIS) {
            window.startedAt = now;
            window.count = 0;
        }
        window.expiresAt = expiresAt(now);
        if (++window.count > rateFor(operation)) {
            throw WebRtcSessionException.tooManyRequests("WEBRTC_RATE_LIMITED");
        }
    }

    private void removeExpired(long now) {
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    private long expiresAt(long now) {
        long ttl = Math.max(WINDOW_MILLIS, properties.getRateLimit().getKeyTtl().toMillis());
        return now > Long.MAX_VALUE - ttl ? Long.MAX_VALUE : now + ttl;
    }

    private int rateFor(WebRtcOperation operation) {
        return switch (operation) {
            case PUBLISH -> properties.getRateLimit().getPublishPerMinute();
            case PLAY -> properties.getRateLimit().getPlayPerMinute();
            case PATCH, DELETE -> properties.getRateLimit().getSessionOperationPerMinute();
        };
    }

    private static final class Window {
        private long startedAt;
        private long expiresAt;
        private int count;

        private Window(long startedAt, long expiresAt) {
            this.startedAt = startedAt;
            this.expiresAt = expiresAt;
        }
    }
}

package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import com.ss.easymedia.webrtc.repository.WebRtcRedisKeys;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import java.time.Duration;

/**
 * 基于 Redisson 的 WebRTC 信令限流器。
 */
public class RedisWebRtcRateLimiter implements WebRtcRateLimiter {

    /** 创建并获取 Redis 分布式限流器。 */
    private final RedissonClient redissonClient;
    /** 生成按操作和主体隔离的限流键。 */
    private final WebRtcRedisKeys keys;
    /** 提供限流开关、阈值和键存活时间。 */
    private final WebRtcProperties properties;

    /**
     * 创建基于 Redis 的 WebRTC 请求限流器。

     *
     * @param redissonClient Redisson 客户端
     * @param keys Redis 键生成器
     * @param properties 模块配置
     */
    public RedisWebRtcRateLimiter(RedissonClient redissonClient, WebRtcRedisKeys keys,
                                  WebRtcProperties properties) {
        this.redissonClient = redissonClient;
        this.keys = keys;
        this.properties = properties;
    }

    /**
     * 消耗一次操作许可；关闭限流时直接放行。
     */
    @Override
    public void check(WebRtcOperation operation, WebRtcIdentity identity, String clientIp) {
        if (!properties.getRateLimit().isEnabled()) {
            return;
        }
        int rate = rateFor(operation);
        String key = keys.rateLimit(operation, identity.rateLimitKey(), clientIp);
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.trySetRate(RateType.OVERALL, rate, Duration.ofMinutes(1));
        limiter.expire(properties.getRateLimit().getKeyTtl());
        if (!limiter.tryAcquire()) {
            throw WebRtcSessionException.tooManyRequests("WEBRTC_RATE_LIMITED");
        }
    }

    /**
     * 读取指定信令操作对应的每分钟许可数。
     */
    private int rateFor(WebRtcOperation operation) {
        return switch (operation) {
            case PUBLISH -> properties.getRateLimit().getPublishPerMinute();
            case PLAY -> properties.getRateLimit().getPlayPerMinute();
            case PATCH, DELETE -> properties.getRateLimit().getSessionOperationPerMinute();
        };
    }
}

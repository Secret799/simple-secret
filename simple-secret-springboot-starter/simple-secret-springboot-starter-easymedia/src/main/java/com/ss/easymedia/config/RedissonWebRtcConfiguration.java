package com.ss.easymedia.config;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.repository.RedissonWebRtcSessionRepository;
import com.ss.easymedia.webrtc.repository.WebRtcRedisKeys;
import com.ss.easymedia.webrtc.repository.WebRtcSessionRepository;
import com.ss.easymedia.webrtc.security.RedisWebRtcRateLimiter;
import com.ss.easymedia.webrtc.security.WebRtcRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 存在时启用的 WebRTC 分布式会话与限流配置。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnBean(RedissonClient.class)
@ConditionalOnProperty(prefix = "simple-secret.easymedia.webrtc", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RedissonWebRtcConfiguration {

    /** @return 基于 Redis 的分布式会话仓库。 */
    @Bean
    @ConditionalOnMissingBean(WebRtcSessionRepository.class)
    public WebRtcSessionRepository redissonWebRtcSessionRepository(
            RedissonClient redissonClient, WebRtcRedisKeys keys) {
        return new RedissonWebRtcSessionRepository(redissonClient, keys);
    }

    /** @return 基于 Redis 的分布式信令限流器。 */
    @Bean
    @ConditionalOnMissingBean(WebRtcRateLimiter.class)
    public WebRtcRateLimiter redisWebRtcRateLimiter(
            RedissonClient redissonClient, WebRtcRedisKeys keys, WebRtcProperties properties) {
        return new RedisWebRtcRateLimiter(redissonClient, keys, properties);
    }
}

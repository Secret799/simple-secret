package com.ss.easymedia.webrtc.repository;

import com.ss.easymedia.webrtc.domain.WebRtcSessionRecord;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Redisson 的 WebRTC 会话仓储。
 */
public class RedissonWebRtcSessionRepository implements WebRtcSessionRepository {

    /** 获取会话分布式锁的最长等待时间。 */
    private static final long LOCK_WAIT_SECONDS = 2;

    /** WebRTC Redis 数据的 Redisson 客户端。 */
    private final RedissonClient redissonClient;
    /** 集中管理会话、锁和补偿队列键名。 */
    private final WebRtcRedisKeys keys;

    /**
     * 创建 Redisson 会话仓储。
     */
    public RedissonWebRtcSessionRepository(RedissonClient redissonClient, WebRtcRedisKeys keys) {
        this.redissonClient = redissonClient;
        this.keys = keys;
    }

    /** @return 会话 ID 未被占用且成功写入时为 true。 */
    @Override
    public boolean create(WebRtcSessionRecord record, Duration ttl) {
        return bucket(record.getSessionId()).setIfAbsent(copy(record), ttl);
    }

    /** @return 脱离 Redis 可变状态的会话副本。 */
    @Override
    public Optional<WebRtcSessionRecord> find(String sessionId) {
        WebRtcSessionRecord record = bucket(sessionId).get();
        return Optional.ofNullable(record).map(RedissonWebRtcSessionRepository::copy);
    }

    /** 保存会话快照并设置新的存活时间。 */
    @Override
    public void save(WebRtcSessionRecord record, Duration ttl) {
        bucket(record.getSessionId()).set(copy(record), ttl);
    }

    /** @return 会话存在且在保留原剩余 TTL 的前提下更新成功时为 true。 */
    @Override
    public boolean savePreservingTtl(WebRtcSessionRecord record) {
        RBucket<WebRtcSessionRecord> bucket = bucket(record.getSessionId());
        long remainingTtlMillis = bucket.remainTimeToLive();
        if (remainingTtlMillis <= 0) {
            return false;
        }
        return bucket.setIfExists(copy(record), Duration.ofMillis(remainingTtlMillis));
    }

    /** @return 会话记录是否已删除；无论结果都会移除关闭索引。 */
    @Override
    public boolean delete(String sessionId) {
        boolean deleted = bucket(sessionId).delete();
        closingSet().remove(sessionId);
        return deleted;
    }

    /** 在 Redisson 可重入锁内执行单个会话的串行化操作。 */
    @Override
    public <T> T withSessionLock(String sessionId, Supplier<T> action) {
        RLock lock = redissonClient.getLock(keys.sessionLock(sessionId));
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw WebRtcSessionException.serviceUnavailable("WEBRTC_SESSION_LOCK_INTERRUPTED");
        }
        if (!acquired) {
            throw WebRtcSessionException.serviceUnavailable("WEBRTC_SESSION_BUSY");
        }
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 将会话按指定重试时间写入关闭补偿有序集合。 */
    @Override
    public void scheduleClosingRetry(String sessionId, Instant retryAt) {
        closingSet().add(retryAt.toEpochMilli(), sessionId);
    }

    /** @return 当前到期且不超过数量上限的会话 ID。 */
    @Override
    public List<String> pollClosingRetries(Instant now, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return new ArrayList<>(closingSet().valueRange(
                Double.NEGATIVE_INFINITY, true, now.toEpochMilli(), true, 0, limit));
    }

    /** 从关闭补偿有序集合中移除指定会话。 */
    @Override
    public void removeClosingRetry(String sessionId) {
        closingSet().remove(sessionId);
    }

    /** @return 指向指定会话键的 Redisson Bucket。 */
    private RBucket<WebRtcSessionRecord> bucket(String sessionId) {
        return redissonClient.getBucket(keys.session(sessionId));
    }

    /** @return 按下次删除重试时间排序的关闭会话集合。 */
    private RScoredSortedSet<String> closingSet() {
        return redissonClient.getScoredSortedSet(keys.closingIndex());
    }

    /**
     * 复制可变会话对象，防止调用方修改仓储内部数据。
     */
    private static WebRtcSessionRecord copy(WebRtcSessionRecord source) {
        return new WebRtcSessionRecord(
                source.getSessionId(), source.getSessionType(), source.getState(),
                source.getTenantId(), source.getSubject(), source.getApp(), source.getStream(),
                source.getMediaNodeId(), source.getUpstreamLocation(), source.getUpstreamEtag(),
                source.getCreatedAt(), source.getUpdatedAt(), source.getNextDeleteRetryAt(),
                source.getDeleteRetryCount());
    }
}

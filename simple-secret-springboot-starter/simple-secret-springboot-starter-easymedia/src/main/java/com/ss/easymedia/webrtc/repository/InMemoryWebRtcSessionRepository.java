package com.ss.easymedia.webrtc.repository;

import com.ss.easymedia.webrtc.domain.WebRtcSessionRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 单机内存 WebRTC 会话仓储。
 * <p>
 * 未配置 RedissonClient 时的回退实现，仅适用于单实例部署：
 * 会话锁为进程内锁，关闭补偿队列不跨节点共享，TTL 以本地时钟近似执行。
 */
public class InMemoryWebRtcSessionRepository implements WebRtcSessionRepository {

    /** 会话快照与过期时间。 */
    private static final class Entry {
        private final WebRtcSessionRecord record;
        private long expiresAt;

        Entry(WebRtcSessionRecord record, long expiresAt) {
            this.record = record;
            this.expiresAt = expiresAt;
        }
    }

    /** 会话锁及当前持有或等待该锁的调用数。 */
    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }

    /** 会话存储。 */
    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    /** 待补偿删除会话：sessionId -> 下次重试时间戳。 */
    private final ConcurrentHashMap<String, Long> closingRetries = new ConcurrentHashMap<>();
    /** 会话级进程内锁。 */
    private final ConcurrentHashMap<String, LockEntry> locks = new ConcurrentHashMap<>();

    @Override
    public boolean create(WebRtcSessionRecord record, Duration ttl) {
        long expiresAt = expiresAt(ttl);
        Entry entry = new Entry(copy(record), expiresAt);
        return store.compute(sessionId(record), (key, existing) ->
                existing != null && !expired(existing) ? existing : entry) == entry;
    }

    @Override
    public Optional<WebRtcSessionRecord> find(String sessionId) {
        Entry entry = store.computeIfPresent(sessionId,
                (key, existing) -> expired(existing) ? null : existing);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(copy(entry.record));
    }

    @Override
    public void save(WebRtcSessionRecord record, Duration ttl) {
        store.put(sessionId(record), new Entry(copy(record), expiresAt(ttl)));
    }

    @Override
    public boolean savePreservingTtl(WebRtcSessionRecord record) {
        String sessionId = sessionId(record);
        return store.compute(sessionId, (key, existing) -> {
            if (existing == null || expired(existing)) {
                return null;
            }
            existing.record.setUpstreamEtag(record.getUpstreamEtag());
            existing.record.setUpdatedAt(record.getUpdatedAt());
            return existing;
        }) != null;
    }

    @Override
    public boolean delete(String sessionId) {
        boolean removed = store.remove(sessionId) != null;
        closingRetries.remove(sessionId);
        return removed;
    }

    @Override
    public <T> T withSessionLock(String sessionId, Supplier<T> action) {
        LockEntry entry = locks.compute(sessionId, (key, existing) -> {
            LockEntry retained = existing == null ? new LockEntry() : existing;
            retained.references++;
            return retained;
        });
        entry.lock.lock();
        try {
            return action.get();
        } finally {
            entry.lock.unlock();
            locks.computeIfPresent(sessionId, (key, current) -> {
                if (current != entry) {
                    return current;
                }
                current.references--;
                return current.references == 0 ? null : current;
            });
        }
    }

    @Override
    public void scheduleClosingRetry(String sessionId, Instant retryAt) {
        closingRetries.put(sessionId, retryAt.toEpochMilli());
    }

    @Override
    public List<String> pollClosingRetries(Instant now, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        long nowMillis = now.toEpochMilli();
        List<String> result = new ArrayList<>(limit);
        closingRetries.forEach((sessionId, retryAt) -> {
            if (retryAt <= nowMillis && result.size() < limit) {
                result.add(sessionId);
            }
        });
        return result;
    }

    @Override
    public void removeClosingRetry(String sessionId) {
        closingRetries.remove(sessionId);
    }

    private static String sessionId(WebRtcSessionRecord record) {
        return record.getSessionId();
    }

    private long expiresAt(Duration ttl) {
        long millis = ttl == null ? 0 : Math.max(0, ttl.toMillis());
        return millis == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + millis;
    }

    private boolean expired(Entry entry) {
        return entry.expiresAt != Long.MAX_VALUE && entry.expiresAt <= System.currentTimeMillis();
    }

    private static WebRtcSessionRecord copy(WebRtcSessionRecord source) {
        return new WebRtcSessionRecord(
                source.getSessionId(), source.getSessionType(), source.getState(),
                source.getTenantId(), source.getSubject(), source.getApp(), source.getStream(),
                source.getMediaNodeId(), source.getUpstreamLocation(), source.getUpstreamEtag(),
                source.getCreatedAt(), source.getUpdatedAt(), source.getNextDeleteRetryAt(),
                source.getDeleteRetryCount());
    }
}

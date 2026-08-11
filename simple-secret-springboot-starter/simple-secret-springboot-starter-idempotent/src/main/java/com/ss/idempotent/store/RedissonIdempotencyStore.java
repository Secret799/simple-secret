package com.ss.idempotent.store;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.Objects;

/** 使用 Redisson bucket 实现分布式原子幂等租约。 */
public final class RedissonIdempotencyStore implements IdempotencyStore {

    private final RedissonClient client;

    /**
     * 创建 Redisson store。
     *
     * @param client 已由应用配置的 Redisson 客户端
     */
    public RedissonIdempotencyStore(RedissonClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public boolean tryAcquire(String key, String owner, Duration ttl) {
        validate(key, owner);
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Idempotency TTL must be positive.");
        }
        return bucket(key).setIfAbsent(owner, ttl);
    }

    @Override
    public boolean release(String key, String owner) {
        validate(key, owner);
        return bucket(key).compareAndSet(owner, null);
    }

    private RBucket<String> bucket(String key) {
        return client.getBucket(key);
    }

    private static void validate(String key, String owner) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency key must not be blank.");
        }
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Idempotency owner must not be blank.");
        }
    }
}

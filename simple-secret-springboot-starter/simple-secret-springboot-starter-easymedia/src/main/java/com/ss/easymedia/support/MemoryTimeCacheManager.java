package com.ss.easymedia.support;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * 内存倒计时缓存管理器。
 * <p>
 * 迁移自 honeybee 的 {@code MemoryTimeCacheManager}，剥离 hutool 依赖：
 * 按写入时间过期，读取时可刷新存活时间；后台 daemon 线程周期清理到期条目，
 * 过期或显式移除时通知监听器（等价于原 {@code TimedCache} 的定时清理语义）。
 *
 * @author JunPzx
 * @since 2025/9/5 08:56
 */
public class MemoryTimeCacheManager<K, V> {

    /** 缓存条目，记录值与过期时刻。 */
    private static final class CacheEntry<V> {
        private final V value;
        private long expiresAt;

        CacheEntry(V value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean expired(long now) {
            return expiresAt < now;
        }
    }

    /** 缓存存活时间，单位毫秒；0 表示永不过期。 */
    private final long timeoutMillis;
    /** 缓存存储。 */
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    /** 数据删除回调。 */
    private final CopyOnWriteArrayList<BiConsumer<K, V>> onRemoveListeners = new CopyOnWriteArrayList<>();
    /** 周期清理到期条目的后台线程。 */
    private final ScheduledExecutorService cleaner;

    /**
     * 构造方法
     *
     * @param timeout 缓存过期时间
     */
    public MemoryTimeCacheManager(Duration timeout) {
        this(Objects.requireNonNull(timeout, "timeout").toMillis());
    }

    /**
     * 构造方法
     *
     * @param timeoutMillis 缓存过期时间，单位毫秒；0 表示永不过期
     */
    public MemoryTimeCacheManager(long timeoutMillis) {
        this.timeoutMillis = Math.max(0, timeoutMillis);
        if (this.timeoutMillis == 0) {
            this.cleaner = null;
        } else {
            long pruneInterval = Math.max(500, this.timeoutMillis / 2);
            this.cleaner = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "memory-time-cache-prune");
                thread.setDaemon(true);
                return thread;
            });
            this.cleaner.scheduleWithFixedDelay(this::pruneExpired,
                    pruneInterval, pruneInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 获取缓存值；不存在或已过期时用加载器创建并写入。
     *
     * @param key              缓存键
     * @param updateLastAccess 是否在命中时刷新存活时间
     * @param loader           缺失值加载器
     * @return 缓存值
     */
    public synchronized V get(K key, boolean updateLastAccess, Supplier<V> loader) {
        long now = System.currentTimeMillis();
        CacheEntry<V> entry = cache.get(key);
        if (entry != null && !entry.expired(now)) {
            if (updateLastAccess) {
                entry.expiresAt = now + timeoutMillis;
            }
            return entry.value;
        }
        if (entry != null) {
            cache.remove(key);
            notifyRemove(key, entry.value);
        }
        V value = Objects.requireNonNull(loader, "loader").get();
        cache.put(key, new CacheEntry<>(value, now + timeoutMillis));
        return value;
    }

    /**
     * 添加缓存移除监听器。
     *
     * @param listener 移除监听器
     */
    public void addOnRemoveListener(BiConsumer<K, V> listener) {
        if (listener != null) {
            onRemoveListeners.add(listener);
        }
    }

    /**
     * 显式移除缓存并触发监听器。
     *
     * @param key 缓存键
     */
    public void remove(K key) {
        CacheEntry<V> entry = cache.remove(key);
        if (entry != null) {
            notifyRemove(key, entry.value);
        }
    }

    /**
     * 清空缓存并触发每个条目的移除监听器。
     */
    public void clear() {
        cache.forEach((key, entry) -> {
            CacheEntry<V> removed = cache.remove(key);
            if (removed != null) {
                notifyRemove(key, removed.value);
            }
        });
    }

    /**
     * 关闭后台清理线程；未配置清理线程时无操作。
     */
    public void destroy() {
        if (cleaner != null) {
            cleaner.shutdown();
        }
    }

    /**
     * 清理全部到期条目并触发监听器。
     */
    private void pruneExpired() {
        long now = System.currentTimeMillis();
        cache.forEach((key, entry) -> {
            if (entry.expired(now)) {
                CacheEntry<V> removed = cache.remove(key);
                if (removed != null) {
                    notifyRemove(key, removed.value);
                }
            }
        });
    }

    /**
     * 触发所有移除监听器。
     */
    private void notifyRemove(K key, V value) {
        for (BiConsumer<K, V> listener : onRemoveListeners) {
            try {
                listener.accept(key, value);
            } catch (RuntimeException ignored) {
                // 移除监听器异常不影响缓存操作
            }
        }
    }
}

package com.ss.common.toolbox.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * 基于 Java 标准库的线程安全过期缓存。
 *
 * <p>缓存默认采用访问时惰性清理。只有显式调用
 * {@link #scheduleCleanup(Duration)} 时才会创建守护清理线程。</p>
 *
 * @param <K> 缓存 key 类型
 * @param <V> 缓存值类型
 */
public final class ExpiringCache<K, V> implements AutoCloseable {
    private static final System.Logger LOGGER =
            System.getLogger(ExpiringCache.class.getName());

    private final ConcurrentMap<K, CacheEntry<V>> entries = new ConcurrentHashMap<>();
    private final ConcurrentMap<K, Object> keyLocks = new ConcurrentHashMap<>();
    private final List<CacheRemovalListener<K, V>> removalListeners =
            new CopyOnWriteArrayList<>();
    private final long defaultTtlNanos;
    private final LongSupplier ticker;

    private ScheduledExecutorService cleanupExecutor;
    private boolean cleanupClosed;

    /**
     * 使用指定默认过期时间创建缓存。
     *
     * @param defaultTtl 默认过期时间，必须大于零
     */
    public ExpiringCache(Duration defaultTtl) {
        this(defaultTtl, System::nanoTime);
    }

    ExpiringCache(Duration defaultTtl, LongSupplier ticker) {
        this.defaultTtlNanos = positiveNanos(defaultTtl, "defaultTtl");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
    }

    /**
     * 使用默认过期时间写入缓存。
     *
     * @param key   缓存 key，不允许为空
     * @param value 缓存值，不允许为空
     */
    public void put(K key, V value) {
        putNanos(key, value, defaultTtlNanos);
    }

    /**
     * 使用单项过期时间写入缓存。
     *
     * @param key   缓存 key，不允许为空
     * @param value 缓存值，不允许为空
     * @param ttl   过期时间，必须大于零
     */
    public void put(K key, V value, Duration ttl) {
        putNanos(key, value, positiveNanos(ttl, "ttl"));
    }

    /**
     * 获取缓存值，访问不会延长过期时间。
     *
     * @param key 缓存 key
     * @return 未过期的值，不存在或已过期时返回 {@code null}
     */
    public V get(K key) {
        return get(key, false);
    }

    /**
     * 获取缓存值，并可选择从本次访问起重新计算过期时间。
     *
     * @param key               缓存 key
     * @param refreshExpiration 是否刷新过期时间
     * @return 未过期的值，不存在或已过期时返回 {@code null}
     */
    public V get(K key, boolean refreshExpiration) {
        Objects.requireNonNull(key, "key");
        CacheEntry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        long now = ticker.getAsLong();
        if (entry.isExpired(now)) {
            expire(key, entry);
            return null;
        }
        if (refreshExpiration) {
            entry.refresh(now);
        }
        return entry.value;
    }

    /**
     * key 不存在或已过期时使用加载器创建值。
     *
     * @param key              缓存 key
     * @param mappingFunction 值加载器，返回空值时不缓存
     * @return 已有值或新加载的值
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        return computeIfAbsentNanos(key, defaultTtlNanos, mappingFunction);
    }

    /**
     * key 不存在或已过期时使用加载器创建具有指定过期时间的值。
     *
     * @param key              缓存 key
     * @param ttl              新值过期时间
     * @param mappingFunction 值加载器，返回空值时不缓存
     * @return 已有值或新加载的值
     */
    public V computeIfAbsent(K key, Duration ttl,
                             Function<? super K, ? extends V> mappingFunction) {
        return computeIfAbsentNanos(key, positiveNanos(ttl, "ttl"), mappingFunction);
    }

    /**
     * 判断未过期的缓存项是否存在。
     *
     * @param key 缓存 key
     * @return 存在时返回 {@code true}
     */
    public boolean containsKey(K key) {
        return get(key) != null;
    }

    /**
     * 显式删除缓存项。
     *
     * @param key 缓存 key
     * @return 被删除的值，不存在时返回 {@code null}
     */
    public V remove(K key) {
        Objects.requireNonNull(key, "key");
        CacheEntry<V> removed = entries.remove(key);
        if (removed == null) {
            return null;
        }
        notifyRemoval(key, removed.value, CacheRemovalCause.EXPLICIT);
        return removed.value;
    }

    /**
     * 清空缓存并按显式删除通知监听器。
     */
    public void clear() {
        List<K> keys = new ArrayList<>(entries.keySet());
        keys.forEach(this::remove);
    }

    /**
     * 返回当前未过期缓存项数量。
     *
     * @return 缓存项数量
     */
    public int size() {
        purgeExpired();
        return entries.size();
    }

    /**
     * 注册移除监听器。监听器异常会被记录，但不会中断缓存操作。
     *
     * @param listener 监听器
     */
    public void addRemovalListener(CacheRemovalListener<K, V> listener) {
        removalListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * 启用周期性过期清理。重复调用不会创建额外线程。
     *
     * @param interval 清理周期，必须大于零
     * @throws IllegalStateException 缓存清理资源已经关闭时抛出
     */
    public synchronized void scheduleCleanup(Duration interval) {
        long intervalNanos = positiveNanos(interval, "interval");
        if (cleanupClosed) {
            throw new IllegalStateException("Cleanup scheduler has been closed");
        }
        if (cleanupExecutor != null) {
            return;
        }
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "simple-secret-expiring-cache-cleaner");
            thread.setDaemon(true);
            return thread;
        });
        cleanupExecutor.scheduleAtFixedRate(
                this::purgeExpired, intervalNanos, intervalNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 判断周期性清理任务是否正在运行。
     *
     * @return 正在运行时返回 {@code true}
     */
    public synchronized boolean isCleanupScheduled() {
        return cleanupExecutor != null && !cleanupExecutor.isShutdown();
    }

    /**
     * 关闭可选的周期性清理线程。缓存数据仍可通过惰性过期方式使用。
     */
    @Override
    public synchronized void close() {
        cleanupClosed = true;
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
            cleanupExecutor = null;
        }
    }

    private void putNanos(K key, V value, long ttlNanos) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        CacheEntry<V> replacement = new CacheEntry<>(value, ttlNanos,
                deadline(ticker.getAsLong(), ttlNanos));
        CacheEntry<V> previous = entries.put(key, replacement);
        if (previous != null) {
            notifyRemoval(key, previous.value, CacheRemovalCause.REPLACED);
        }
    }

    private V computeIfAbsentNanos(K key, long ttlNanos,
                                   Function<? super K, ? extends V> mappingFunction) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mappingFunction, "mappingFunction");
        V existing = get(key);
        if (existing != null) {
            return existing;
        }

        Object keyLock = keyLocks.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (keyLock) {
                existing = get(key);
                if (existing != null) {
                    return existing;
                }
                V loaded = mappingFunction.apply(key);
                if (loaded != null) {
                    putNanos(key, loaded, ttlNanos);
                }
                return loaded;
            }
        } finally {
            keyLocks.remove(key, keyLock);
        }
    }

    private void purgeExpired() {
        long now = ticker.getAsLong();
        entries.forEach((key, entry) -> {
            if (entry.isExpired(now)) {
                expire(key, entry);
            }
        });
    }

    private void expire(K key, CacheEntry<V> entry) {
        if (entries.remove(key, entry)) {
            notifyRemoval(key, entry.value, CacheRemovalCause.EXPIRED);
        }
    }

    private void notifyRemoval(K key, V value, CacheRemovalCause cause) {
        for (CacheRemovalListener<K, V> listener : removalListeners) {
            try {
                listener.onRemoval(key, value, cause);
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Cache removal listener failed", exception);
            }
        }
    }

    private static long positiveNanos(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        long nanos;
        try {
            nanos = duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " is too large", exception);
        }
        if (nanos <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return nanos;
    }

    private static long deadline(long now, long ttlNanos) {
        long value = now + ttlNanos;
        return value < now ? Long.MAX_VALUE : value;
    }

    private static final class CacheEntry<V> {
        private final V value;
        private final long ttlNanos;
        private volatile long expiresAtNanos;

        private CacheEntry(V value, long ttlNanos, long expiresAtNanos) {
            this.value = value;
            this.ttlNanos = ttlNanos;
            this.expiresAtNanos = expiresAtNanos;
        }

        private boolean isExpired(long now) {
            return now >= expiresAtNanos;
        }

        private void refresh(long now) {
            expiresAtNanos = deadline(now, ttlNanos);
        }
    }
}

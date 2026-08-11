package com.ss.common.toolbox.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * 将过期缓存与外部持久化存储更新组合起来的抽象管理器。
 *
 * <p>只有新 key 或值发生变化时才触发变化回调。调用方可按次决定是否同步更新外部存储。</p>
 *
 * @param <K> 缓存 key 类型
 * @param <V> 缓存值类型
 */
public abstract class DatabaseExpiringCacheManager<K, V> implements AutoCloseable {
    private static final System.Logger LOGGER =
            System.getLogger(DatabaseExpiringCacheManager.class.getName());

    private final ExpiringCache<K, V> cache;
    private final ValueComparator<V> comparator;

    private BiConsumer<K, V> storeUpdateSucceeded;
    private BiConsumer<K, V> storeUpdateFailed;
    private BiConsumer<K, V> valueChanged;
    private BiConsumer<K, V> expired;

    /**
     * 创建数据库联动缓存。
     *
     * @param defaultTtl 默认过期时间
     * @param comparator 值比较器
     */
    protected DatabaseExpiringCacheManager(Duration defaultTtl,
                                           ValueComparator<V> comparator) {
        this(defaultTtl, comparator, System::nanoTime);
    }

    DatabaseExpiringCacheManager(Duration defaultTtl, ValueComparator<V> comparator,
                                 LongSupplier ticker) {
        this.cache = new ExpiringCache<>(defaultTtl, ticker);
        this.comparator = Objects.requireNonNull(comparator, "comparator");
        this.cache.addRemovalListener((key, value, cause) -> {
            if (cause == CacheRemovalCause.EXPIRED) {
                acceptSafely(expired, key, value, "Cache expiration callback failed");
            }
        });
    }

    /**
     * 使用默认 TTL 写入缓存。
     *
     * @param key                 缓存 key
     * @param value               缓存值
     * @param updateStoreOnChange 值变化时是否同步更新外部存储
     */
    public synchronized void put(K key, V value, boolean updateStoreOnChange) {
        put(key, value, null, updateStoreOnChange);
    }

    /**
     * 使用单项 TTL 写入缓存。
     *
     * @param key                 缓存 key
     * @param value               缓存值
     * @param ttl                 单项过期时间，为空时使用默认值
     * @param updateStoreOnChange 值变化时是否同步更新外部存储
     */
    public synchronized void put(K key, V value, Duration ttl,
                                 boolean updateStoreOnChange) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        V current = cache.get(key);
        boolean changed = current == null || comparator.isNotEqual(current, value);
        if (changed) {
            acceptSafely(valueChanged, key, value, "Cache value change callback failed");
            if (updateStoreOnChange) {
                boolean updated = updateStore(key, value);
                acceptSafely(updated ? storeUpdateSucceeded : storeUpdateFailed,
                        key, value, updated
                                ? "Store update success callback failed"
                                : "Store update failure callback failed");
            }
        }
        if (ttl == null) {
            cache.put(key, value);
        } else {
            cache.put(key, value, ttl);
        }
    }

    /**
     * 获取未过期缓存值。
     *
     * @param key 缓存 key
     * @return 缓存值，不存在或过期时返回 {@code null}
     */
    public V get(K key) {
        return cache.get(key);
    }

    /**
     * 判断未过期缓存项是否存在。
     *
     * @param key 缓存 key
     * @return 存在时返回 {@code true}
     */
    public boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    /**
     * 显式删除缓存项。
     *
     * @param key 缓存 key
     * @return 被删除的值
     */
    public V remove(K key) {
        return cache.remove(key);
    }

    /**
     * 启用周期性过期清理。
     *
     * @param interval 清理周期
     */
    public void scheduleCleanup(Duration interval) {
        cache.scheduleCleanup(interval);
    }

    /**
     * 设置外部存储更新成功回调。
     *
     * @param callback 回调
     */
    protected final void onStoreUpdateSucceeded(BiConsumer<K, V> callback) {
        this.storeUpdateSucceeded = callback;
    }

    /**
     * 设置外部存储更新失败回调。
     *
     * @param callback 回调
     */
    protected final void onStoreUpdateFailed(BiConsumer<K, V> callback) {
        this.storeUpdateFailed = callback;
    }

    /**
     * 设置缓存值变化回调。
     *
     * @param callback 回调
     */
    protected final void onValueChanged(BiConsumer<K, V> callback) {
        this.valueChanged = callback;
    }

    /**
     * 设置缓存过期回调。
     *
     * @param callback 回调
     */
    protected final void onExpired(BiConsumer<K, V> callback) {
        this.expired = callback;
    }

    /**
     * 更新外部持久化存储。
     *
     * @param key   缓存 key
     * @param value 新值
     * @return 更新成功时返回 {@code true}
     */
    protected abstract boolean updateStore(K key, V value);

    /**
     * 关闭可选的周期性清理线程。
     */
    @Override
    public void close() {
        cache.close();
    }

    private void acceptSafely(BiConsumer<K, V> callback, K key, V value,
                              String failureMessage) {
        if (callback == null) {
            return;
        }
        try {
            callback.accept(key, value);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, failureMessage, exception);
        }
    }
}

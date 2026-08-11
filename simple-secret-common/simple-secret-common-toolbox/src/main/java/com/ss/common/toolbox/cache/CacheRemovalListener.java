package com.ss.common.toolbox.cache;

/**
 * 缓存项移除监听器。
 *
 * @param <K> 缓存 key 类型
 * @param <V> 缓存值类型
 */
@FunctionalInterface
public interface CacheRemovalListener<K, V> {

    /**
     * 缓存项被移除时调用。
     *
     * @param key   缓存 key
     * @param value 被移除的值
     * @param cause 移除原因
     */
    void onRemoval(K key, V value, CacheRemovalCause cause);
}

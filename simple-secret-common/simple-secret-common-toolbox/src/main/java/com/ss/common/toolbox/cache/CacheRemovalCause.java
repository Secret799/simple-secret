package com.ss.common.toolbox.cache;

/**
 * 缓存项被移除的原因。
 */
public enum CacheRemovalCause {
    /** 缓存项达到过期时间。 */
    EXPIRED,
    /** 调用方显式删除缓存项。 */
    EXPLICIT,
    /** 相同 key 写入新值并替换旧值。 */
    REPLACED
}

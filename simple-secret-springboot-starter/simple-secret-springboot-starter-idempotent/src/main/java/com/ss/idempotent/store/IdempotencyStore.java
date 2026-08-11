package com.ss.idempotent.store;

import java.time.Duration;

/** 管理带所有者标识和 TTL 的幂等租约。 */
public interface IdempotencyStore {

    /**
     * 原子获取租约。
     *
     * @param key 固定长度幂等 key
     * @param owner 当前调用的随机所有者标识
     * @param ttl 租约有效期
     * @return true 表示获取成功，false 表示已有有效租约
     */
    boolean tryAcquire(String key, String owner, Duration ttl);

    /**
     * 仅在所有者匹配时原子释放租约。
     *
     * @param key 幂等 key
     * @param owner 获取租约时使用的所有者标识
     * @return true 表示释放成功
     */
    boolean release(String key, String owner);
}

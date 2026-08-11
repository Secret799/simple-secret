package com.ss.redis.exception;

/**
 * Redis 分布式锁获取或释放失败。
 */
public class RedisLockException extends RuntimeException {

    private final String operation;
    private final String key;

    /** 创建不带原始异常的锁异常。 */
    public RedisLockException(String operation, String key) {
        this(operation, key, null);
    }

    /** 创建带原始异常的锁异常。 */
    public RedisLockException(String operation, String key, Throwable cause) {
        super("Redis lock operation '" + operation + "' failed for key '" + key + "'", cause);
        this.operation = operation;
        this.key = key;
    }

    public String getOperation() {
        return operation;
    }

    public String getKey() {
        return key;
    }
}

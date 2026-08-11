package com.ss.redis.exception;

/**
 * Redis 操作失败。
 *
 * <p>异常消息只记录操作和 key，避免把值、凭据或服务端敏感文本写入日志。</p>
 */
public class RedisOperationException extends RuntimeException {

    private final String operation;
    private final String key;

    /**
     * 创建 Redis 操作异常。
     *
     * @param operation 操作名称
     * @param key Redis key
     * @param cause 原始异常
     */
    public RedisOperationException(String operation, String key, Throwable cause) {
        super("Redis operation '" + operation + "' failed for key '" + key + "'", cause);
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

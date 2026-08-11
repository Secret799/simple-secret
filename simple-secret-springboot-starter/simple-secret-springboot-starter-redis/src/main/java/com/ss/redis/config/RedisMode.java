package com.ss.redis.config;

/**
 * Redisson 客户端连接模式。
 */
public enum RedisMode {

    /** 单机 Redis。 */
    SINGLE,

    /** Redis Cluster。 */
    CLUSTER
}

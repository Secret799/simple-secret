package com.ss.redis.operation;

import com.ss.redis.exception.RedisOperationException;
import org.redisson.api.RTopic;

import java.util.Objects;

/**
 * Redis 发布订阅 listener 的可关闭句柄。
 */
public final class RedisSubscription implements AutoCloseable {

    private final String channel;
    private final RTopic topic;
    private final int listenerId;
    private boolean closed;

    RedisSubscription(String channel, RTopic topic, int listenerId) {
        this.channel = Objects.requireNonNull(channel, "channel must not be null");
        this.topic = Objects.requireNonNull(topic, "topic must not be null");
        this.listenerId = listenerId;
    }

    /** 移除当前订阅创建的 listener；重复关闭不会重复访问 Redis。 */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            topic.removeListener(listenerId);
            closed = true;
        } catch (RuntimeException exception) {
            throw new RedisOperationException("unsubscribe", channel, exception);
        }
    }
}

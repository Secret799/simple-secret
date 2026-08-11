package com.ss.redis.operation;

import com.ss.redis.exception.RedisOperationException;
import org.redisson.api.RBlockingQueue;

import java.util.Objects;

/**
 * Redis 阻塞队列元素订阅的可关闭句柄。
 */
public final class RedisQueueSubscription implements AutoCloseable {

    private final String queueName;
    private final RBlockingQueue<?> queue;
    private final int listenerId;
    private boolean closed;

    RedisQueueSubscription(String queueName, RBlockingQueue<?> queue, int listenerId) {
        this.queueName = Objects.requireNonNull(queueName, "queueName must not be null");
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
        this.listenerId = listenerId;
    }

    /** 取消当前 listener；重复关闭不会重复访问 Redis。 */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            queue.unsubscribe(listenerId);
            closed = true;
        } catch (RuntimeException exception) {
            throw new RedisOperationException("unsubscribeQueue", queueName, exception);
        }
    }
}

package com.ss.redis.operation;

import com.ss.redis.exception.RedisOperationException;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RBoundedBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RPriorityBlockingQueue;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 基于 Redisson 的队列操作门面。
 */
public class RedissonQueueOperations {

    protected final RedissonClient client;

    /**
     * 创建 Redis 队列操作门面。
     *
     * @param client Redisson 客户端
     */
    public RedissonQueueOperations(RedissonClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /** 向普通阻塞队列添加元素。 */
    public <T> boolean offer(String queueName, T value) {
        String validatedName = requireQueueName(queueName);
        T validatedValue = requireValue(value);
        return execute("offer", validatedName,
                () -> client.<T>getBlockingQueue(validatedName).offer(validatedValue));
    }

    /** 从普通阻塞队列立即获取一个元素。 */
    public <T> T poll(String queueName, Class<T> type) {
        String validatedName = requireQueueName(queueName);
        Class<T> validatedType = Objects.requireNonNull(type, "type must not be null");
        return execute("poll", validatedName,
                () -> validatedType.cast(client.getBlockingQueue(validatedName).poll()));
    }

    /** 从普通阻塞队列删除一个相等元素。 */
    public <T> boolean remove(String queueName, T value) {
        String validatedName = requireQueueName(queueName);
        T validatedValue = requireValue(value);
        return execute("remove", validatedName,
                () -> client.<T>getBlockingQueue(validatedName).remove(validatedValue));
    }

    /** 删除普通阻塞队列。 */
    public boolean delete(String queueName) {
        String validatedName = requireQueueName(queueName);
        return execute("delete", validatedName,
                () -> client.getBlockingQueue(validatedName).delete());
    }

    /**
     * 向旧式 Redisson 延迟队列添加元素。
     *
     * @deprecated Redisson 3.52 已弃用 {@link RDelayedQueue}，新系统应使用
     * {@code RReliableQueue} 的 delay 能力。
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    @SuppressWarnings("deprecation")
    public <T> void offerDelayed(String queueName, T value, Duration delay) {
        String validatedName = requireQueueName(queueName);
        T validatedValue = requireValue(value);
        Duration validatedDelay = requirePositiveDuration("delay", delay);
        executeVoid("offerDelayed", validatedName, () -> {
            RBlockingQueue<T> destination = client.getBlockingQueue(validatedName);
            client.getDelayedQueue(destination)
                    .offer(validatedValue, validatedDelay.toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    /**
     * 从旧式 Redisson 延迟队列删除元素。
     *
     * @deprecated Redisson 3.52 已弃用 {@link RDelayedQueue}。
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    @SuppressWarnings("deprecation")
    public <T> boolean removeDelayed(String queueName, T value) {
        String validatedName = requireQueueName(queueName);
        T validatedValue = requireValue(value);
        return execute("removeDelayed", validatedName, () -> {
            RBlockingQueue<T> destination = client.getBlockingQueue(validatedName);
            return client.getDelayedQueue(destination).remove(validatedValue);
        });
    }

    /**
     * 销毁旧式 Redisson 延迟队列。
     *
     * @deprecated Redisson 3.52 已弃用 {@link RDelayedQueue}。
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    @SuppressWarnings("deprecation")
    public void destroyDelayed(String queueName) {
        String validatedName = requireQueueName(queueName);
        executeVoid("destroyDelayed", validatedName, () -> {
            RBlockingQueue<Object> destination = client.getBlockingQueue(validatedName);
            client.getDelayedQueue(destination).destroy();
        });
    }

    /** 向优先阻塞队列添加元素。 */
    public <T> boolean offerPriority(String queueName, T value) {
        String validatedName = requireQueueName(queueName);
        T validatedValue = requireValue(value);
        return execute("offerPriority", validatedName,
                () -> client.<T>getPriorityBlockingQueue(validatedName).offer(validatedValue));
    }

    /** 从优先阻塞队列立即获取一个元素。 */
    public <T> T pollPriority(String queueName, Class<T> type) {
        String validatedName = requireQueueName(queueName);
        Class<T> validatedType = Objects.requireNonNull(type, "type must not be null");
        return execute("pollPriority", validatedName,
                () -> validatedType.cast(client.getPriorityBlockingQueue(validatedName).poll()));
    }

    /** 从优先阻塞队列删除一个相等元素。 */
    public <T> boolean removePriority(String queueName, T value) {
        String validatedName = requireQueueName(queueName);
        T validatedValue = requireValue(value);
        return execute("removePriority", validatedName,
                () -> client.<T>getPriorityBlockingQueue(validatedName).remove(validatedValue));
    }

    /** 删除优先阻塞队列。 */
    public boolean deletePriority(String queueName) {
        String validatedName = requireQueueName(queueName);
        return execute("deletePriority", validatedName,
                () -> client.getPriorityBlockingQueue(validatedName).delete());
    }

    /**
     * 初始化旧式有界阻塞队列容量。
     *
     * @deprecated Redisson 3.52 已弃用 {@link RBoundedBlockingQueue}，新系统应使用
     * {@code RReliableQueue} 的 queue size limit 能力。
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    @SuppressWarnings("deprecation")
    public boolean trySetCapacity(String queueName, int capacity) {
        String validatedName = requireQueueName(queueName);
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        return execute("trySetCapacity", validatedName,
                () -> client.getBoundedBlockingQueue(validatedName).trySetCapacity(capacity));
    }

    /**
     * 向旧式有界阻塞队列添加元素。
     *
     * @deprecated Redisson 3.52 已弃用 {@link RBoundedBlockingQueue}。
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    @SuppressWarnings("deprecation")
    public <T> boolean offerBounded(String queueName, T value) {
        String validatedName = requireQueueName(queueName);
        T validatedValue = requireValue(value);
        return execute("offerBounded", validatedName,
                () -> client.<T>getBoundedBlockingQueue(validatedName).offer(validatedValue));
    }

    /**
     * 从旧式有界阻塞队列立即获取一个元素。
     *
     * @deprecated Redisson 3.52 已弃用 {@link RBoundedBlockingQueue}。
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    @SuppressWarnings("deprecation")
    public <T> T pollBounded(String queueName, Class<T> type) {
        String validatedName = requireQueueName(queueName);
        Class<T> validatedType = Objects.requireNonNull(type, "type must not be null");
        return execute("pollBounded", validatedName,
                () -> validatedType.cast(client.getBoundedBlockingQueue(validatedName).poll()));
    }

    /**
     * 从旧式有界阻塞队列删除元素。
     *
     * @deprecated Redisson 3.52 已弃用 {@link RBoundedBlockingQueue}。
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    @SuppressWarnings("deprecation")
    public <T> boolean removeBounded(String queueName, T value) {
        String validatedName = requireQueueName(queueName);
        T validatedValue = requireValue(value);
        return execute("removeBounded", validatedName,
                () -> client.<T>getBoundedBlockingQueue(validatedName).remove(validatedValue));
    }

    /**
     * 删除旧式有界阻塞队列。
     *
     * @deprecated Redisson 3.52 已弃用 {@link RBoundedBlockingQueue}。
     */
    @Deprecated(since = "1.1.0", forRemoval = false)
    @SuppressWarnings("deprecation")
    public boolean deleteBounded(String queueName) {
        String validatedName = requireQueueName(queueName);
        return execute("deleteBounded", validatedName,
                () -> client.getBoundedBlockingQueue(validatedName).delete());
    }

    /** 订阅普通阻塞队列元素并返回可关闭句柄。 */
    public <T> RedisQueueSubscription subscribe(String queueName, Consumer<T> consumer) {
        String validatedName = requireQueueName(queueName);
        Consumer<T> validatedConsumer = Objects.requireNonNull(consumer, "consumer must not be null");
        return execute("subscribeQueue", validatedName, () -> {
            RBlockingQueue<T> queue = client.getBlockingQueue(validatedName);
            int listenerId = queue.subscribeOnElements(value -> {
                try {
                    validatedConsumer.accept(value);
                    return CompletableFuture.completedFuture(null);
                } catch (RuntimeException exception) {
                    return CompletableFuture.failedFuture(exception);
                }
            });
            return new RedisQueueSubscription(validatedName, queue, listenerId);
        });
    }

    private <T> T execute(String operation, String queueName, Supplier<T> action) {
        try {
            return action.get();
        } catch (RedisOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RedisOperationException(operation, queueName, exception);
        }
    }

    private void executeVoid(String operation, String queueName, Runnable action) {
        execute(operation, queueName, () -> {
            action.run();
            return null;
        });
    }

    private static String requireQueueName(String queueName) {
        Objects.requireNonNull(queueName, "queueName must not be null");
        if (queueName.isBlank()) {
            throw new IllegalArgumentException("queueName must not be blank");
        }
        return queueName;
    }

    private static <T> T requireValue(T value) {
        return Objects.requireNonNull(value, "value must not be null");
    }

    private static Duration requirePositiveDuration(String name, Duration duration) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}

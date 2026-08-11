package com.ss.redis.operation;

import com.ss.redis.exception.RedisOperationException;
import com.ss.redis.exception.RedisLockException;
import org.redisson.api.RKeys;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RSet;
import org.redisson.api.RTopic;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 基于 Redisson 的 Redis 数据与协调操作门面。
 *
 * <p>具体操作按能力分组逐步提供，客户端始终由调用方注入。</p>
 */
public class RedissonOperations {

    protected final RedissonClient client;

    /**
     * 创建 Redis 操作门面。
     *
     * @param client Redisson 客户端
     */
    public RedissonOperations(RedissonClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /** 写入对象且不设置过期时间。 */
    public <T> void set(String key, T value) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        executeVoid("set", validatedKey,
                () -> client.<T>getBucket(validatedKey).set(validatedValue));
    }

    /** 写入对象并设置过期时间。 */
    public <T> void set(String key, T value, Duration ttl) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        Duration validatedTtl = requirePositiveDuration("ttl", ttl);
        executeVoid("set", validatedKey,
                () -> client.<T>getBucket(validatedKey).set(validatedValue, validatedTtl));
    }

    /** 替换对象并保留现有 TTL。 */
    public <T> void setKeepingTtl(String key, T value) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        executeVoid("setKeepingTtl", validatedKey,
                () -> client.<T>getBucket(validatedKey).setAndKeepTTL(validatedValue));
    }

    /** key 不存在时写入对象。 */
    public <T> boolean setIfAbsent(String key, T value) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        return execute("setIfAbsent", validatedKey,
                () -> client.<T>getBucket(validatedKey).setIfAbsent(validatedValue));
    }

    /** key 不存在时写入对象并设置 TTL。 */
    public <T> boolean setIfAbsent(String key, T value, Duration ttl) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        Duration validatedTtl = requirePositiveDuration("ttl", ttl);
        return execute("setIfAbsent", validatedKey,
                () -> client.<T>getBucket(validatedKey).setIfAbsent(validatedValue, validatedTtl));
    }

    /** key 已存在时替换对象。 */
    public <T> boolean setIfExists(String key, T value) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        return execute("setIfExists", validatedKey,
                () -> client.<T>getBucket(validatedKey).setIfExists(validatedValue));
    }

    /** key 已存在时替换对象并设置 TTL。 */
    public <T> boolean setIfExists(String key, T value, Duration ttl) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        Duration validatedTtl = requirePositiveDuration("ttl", ttl);
        return execute("setIfExists", validatedKey,
                () -> client.<T>getBucket(validatedKey).setIfExists(validatedValue, validatedTtl));
    }

    /** 读取对象，并在运行时校验返回类型。 */
    public <T> T get(String key, Class<T> type) {
        String validatedKey = requireKey(key);
        Class<T> validatedType = Objects.requireNonNull(type, "type must not be null");
        return execute("get", validatedKey,
                () -> validatedType.cast(client.getBucket(validatedKey).get()));
    }

    /** 删除对象。 */
    public boolean delete(String key) {
        String validatedKey = requireKey(key);
        return execute("delete", validatedKey,
                () -> client.getBucket(validatedKey).delete());
    }

    /** 判断对象是否存在。 */
    public boolean exists(String key) {
        String validatedKey = requireKey(key);
        return execute("exists", validatedKey,
                () -> client.getBucket(validatedKey).isExists());
    }

    /** 设置对象 TTL。 */
    public boolean expire(String key, Duration ttl) {
        String validatedKey = requireKey(key);
        Duration validatedTtl = requirePositiveDuration("ttl", ttl);
        return execute("expire", validatedKey,
                () -> client.getBucket(validatedKey).expire(validatedTtl));
    }

    /**
     * 读取对象剩余 TTL。
     *
     * <p>Redis 的 {@code -1ms} 和 {@code -2ms} 特殊值会原样保留。</p>
     */
    public Duration ttl(String key) {
        String validatedKey = requireKey(key);
        return execute("ttl", validatedKey,
                () -> Duration.ofMillis(client.getBucket(validatedKey).remainTimeToLive()));
    }

    /** 向 List 尾部追加一个值。 */
    public <T> boolean addToList(String key, T value) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        return execute("addToList", validatedKey,
                () -> client.<T>getList(validatedKey).add(validatedValue));
    }

    /** 向 List 尾部批量追加值；空集合不访问 Redis。 */
    public <T> boolean addAllToList(String key, Collection<? extends T> values) {
        String validatedKey = requireKey(key);
        Collection<? extends T> validatedValues = requireValues(values);
        if (validatedValues.isEmpty()) {
            return false;
        }
        return execute("addAllToList", validatedKey,
                () -> client.<T>getList(validatedKey).addAll(validatedValues));
    }

    /** 获取不可变 List 快照。 */
    public <T> List<T> getList(String key, Class<T> type) {
        String validatedKey = requireKey(key);
        Class<T> validatedType = Objects.requireNonNull(type, "type must not be null");
        return execute("getList", validatedKey,
                () -> immutableList(client.getList(validatedKey).readAll(), validatedType));
    }

    /** 获取指定闭区间的不可变 List 快照。 */
    public <T> List<T> getListRange(String key, int start, int end, Class<T> type) {
        String validatedKey = requireKey(key);
        requireRange(start, end);
        Class<T> validatedType = Objects.requireNonNull(type, "type must not be null");
        return execute("getListRange", validatedKey,
                () -> immutableList(client.getList(validatedKey).range(start, end), validatedType));
    }

    /** 读取 List 指定下标。 */
    public <T> T getListValue(String key, int index, Class<T> type) {
        String validatedKey = requireKey(key);
        requireIndex(index);
        Class<T> validatedType = Objects.requireNonNull(type, "type must not be null");
        return execute("getListValue", validatedKey,
                () -> validatedType.cast(client.getList(validatedKey).get(index)));
    }

    /** 替换 List 指定下标并返回旧值。 */
    public <T> T setListValue(String key, int index, T value, Class<T> type) {
        String validatedKey = requireKey(key);
        requireIndex(index);
        T validatedValue = requireValue(value);
        Class<T> validatedType = Objects.requireNonNull(type, "type must not be null");
        return execute("setListValue", validatedKey,
                () -> validatedType.cast(client.<T>getList(validatedKey).set(index, validatedValue)));
    }

    /** 从 List 删除首个相等值。 */
    public <T> boolean removeFromList(String key, T value) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        return execute("removeFromList", validatedKey,
                () -> client.<T>getList(validatedKey).remove(validatedValue));
    }

    /** 向 Set 添加一个值。 */
    public <T> boolean addToSet(String key, T value) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        return execute("addToSet", validatedKey,
                () -> client.<T>getSet(validatedKey).add(validatedValue));
    }

    /** 向 Set 批量添加值；空集合不访问 Redis。 */
    public <T> boolean addAllToSet(String key, Collection<? extends T> values) {
        String validatedKey = requireKey(key);
        Collection<? extends T> validatedValues = requireValues(values);
        if (validatedValues.isEmpty()) {
            return false;
        }
        return execute("addAllToSet", validatedKey,
                () -> client.<T>getSet(validatedKey).addAll(validatedValues));
    }

    /** 获取不可变 Set 快照。 */
    public <T> Set<T> getSet(String key, Class<T> type) {
        String validatedKey = requireKey(key);
        Class<T> validatedType = Objects.requireNonNull(type, "type must not be null");
        return execute("getSet", validatedKey,
                () -> immutableSet(client.getSet(validatedKey).readAll(), validatedType));
    }

    /** 从 Set 删除值。 */
    public <T> boolean removeFromSet(String key, T value) {
        String validatedKey = requireKey(key);
        T validatedValue = requireValue(value);
        return execute("removeFromSet", validatedKey,
                () -> client.<T>getSet(validatedKey).remove(validatedValue));
    }

    /** 写入 Map 字段并返回旧值。 */
    public <K, V> V putToMap(String key, K mapKey, V value, Class<V> valueType) {
        String validatedKey = requireKey(key);
        K validatedMapKey = requireValue(mapKey);
        V validatedValue = requireValue(value);
        Class<V> validatedType = Objects.requireNonNull(valueType, "valueType must not be null");
        return execute("putToMap", validatedKey,
                () -> validatedType.cast(client.<K, V>getMap(validatedKey)
                        .put(validatedMapKey, validatedValue)));
    }

    /** 批量写入 Map；空 Map 不访问 Redis。 */
    public <K, V> void putAllToMap(String key, Map<? extends K, ? extends V> values) {
        String validatedKey = requireKey(key);
        Map<? extends K, ? extends V> validatedValues = requireMap(values);
        if (validatedValues.isEmpty()) {
            return;
        }
        executeVoid("putAllToMap", validatedKey,
                () -> client.<K, V>getMap(validatedKey).putAll(validatedValues));
    }

    /** 读取 Map 字段。 */
    public <K, V> V getFromMap(String key, K mapKey, Class<V> valueType) {
        String validatedKey = requireKey(key);
        K validatedMapKey = requireValue(mapKey);
        Class<V> validatedType = Objects.requireNonNull(valueType, "valueType must not be null");
        return execute("getFromMap", validatedKey,
                () -> validatedType.cast(client.getMap(validatedKey).get(validatedMapKey)));
    }

    /** 获取不可变 Map 快照。 */
    public <K, V> Map<K, V> getMap(String key, Class<K> keyType, Class<V> valueType) {
        String validatedKey = requireKey(key);
        Class<K> validatedKeyType = Objects.requireNonNull(keyType, "keyType must not be null");
        Class<V> validatedValueType = Objects.requireNonNull(valueType, "valueType must not be null");
        return execute("getMap", validatedKey,
                () -> immutableMap(client.getMap(validatedKey).readAllMap(),
                        validatedKeyType, validatedValueType));
    }

    /** 删除 Map 字段并返回旧值。 */
    public <K, V> V removeFromMap(String key, K mapKey, Class<V> valueType) {
        String validatedKey = requireKey(key);
        K validatedMapKey = requireValue(mapKey);
        Class<V> validatedType = Objects.requireNonNull(valueType, "valueType must not be null");
        return execute("removeFromMap", validatedKey,
                () -> validatedType.cast(client.getMap(validatedKey).remove(validatedMapKey)));
    }

    /** 判断 Map 字段是否存在。 */
    public <K> boolean containsMapKey(String key, K mapKey) {
        String validatedKey = requireKey(key);
        K validatedMapKey = requireValue(mapKey);
        return execute("containsMapKey", validatedKey,
                () -> client.getMap(validatedKey).containsKey(validatedMapKey));
    }

    /** 设置原子 long。 */
    public void setAtomicLong(String key, long value) {
        String validatedKey = requireKey(key);
        executeVoid("setAtomicLong", validatedKey,
                () -> client.getAtomicLong(validatedKey).set(value));
    }

    /** 读取原子 long。 */
    public long getAtomicLong(String key) {
        String validatedKey = requireKey(key);
        return execute("getAtomicLong", validatedKey,
                () -> client.getAtomicLong(validatedKey).get());
    }

    /** 原子递增并返回新值。 */
    public long incrementAtomicLong(String key) {
        String validatedKey = requireKey(key);
        return execute("incrementAtomicLong", validatedKey,
                () -> client.getAtomicLong(validatedKey).incrementAndGet());
    }

    /** 原子递减并返回新值。 */
    public long decrementAtomicLong(String key) {
        String validatedKey = requireKey(key);
        return execute("decrementAtomicLong", validatedKey,
                () -> client.getAtomicLong(validatedKey).decrementAndGet());
    }

    /**
     * 使用 Redisson watchdog 锁执行操作。
     *
     * @param key 锁名称
     * @param waitTime 最长等待时间
     * @param action 获取锁后执行的操作
     */
    public <T> T withLock(String key, Duration waitTime, Supplier<T> action) {
        String validatedKey = requireKey(key);
        Duration validatedWait = requirePositiveDuration("waitTime", waitTime);
        Supplier<T> validatedAction = Objects.requireNonNull(action, "action must not be null");
        return executeWithLock(validatedKey, validatedAction,
                lock -> lock.tryLock(validatedWait.toMillis(), TimeUnit.MILLISECONDS));
    }

    /**
     * 使用显式 lease time 的 Redisson 锁执行操作。
     *
     * @param key 锁名称
     * @param waitTime 最长等待时间
     * @param leaseTime 锁自动释放时间
     * @param action 获取锁后执行的操作
     */
    public <T> T withLock(String key, Duration waitTime, Duration leaseTime, Supplier<T> action) {
        String validatedKey = requireKey(key);
        Duration validatedWait = requirePositiveDuration("waitTime", waitTime);
        Duration validatedLease = requirePositiveDuration("leaseTime", leaseTime);
        Supplier<T> validatedAction = Objects.requireNonNull(action, "action must not be null");
        return executeWithLock(validatedKey, validatedAction,
                lock -> lock.tryLock(validatedWait.toMillis(), validatedLease.toMillis(),
                        TimeUnit.MILLISECONDS));
    }

    /**
     * 初始化限流配置（若尚未初始化）并立即尝试获取一个许可。
     */
    public boolean tryAcquire(String key, RateType type, long rate, Duration interval) {
        String validatedKey = requireKey(key);
        RateType validatedType = Objects.requireNonNull(type, "type must not be null");
        if (rate <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        Duration validatedInterval = requirePositiveDuration("interval", interval);
        return execute("tryAcquire", validatedKey, () -> {
            RRateLimiter limiter = client.getRateLimiter(validatedKey);
            limiter.trySetRate(validatedType, rate, validatedInterval);
            return limiter.tryAcquire();
        });
    }

    /** 发布消息并返回收到消息的订阅者数量。 */
    public <T> long publish(String channel, T message) {
        String validatedChannel = requireKey(channel);
        T validatedMessage = requireValue(message);
        return execute("publish", validatedChannel,
                () -> client.getTopic(validatedChannel).publish(validatedMessage));
    }

    /** 订阅频道并返回可精确移除 listener 的关闭句柄。 */
    public <T> RedisSubscription subscribe(String channel, Class<T> type, Consumer<T> consumer) {
        String validatedChannel = requireKey(channel);
        Class<T> validatedType = Objects.requireNonNull(type, "type must not be null");
        Consumer<T> validatedConsumer = Objects.requireNonNull(consumer, "consumer must not be null");
        return execute("subscribe", validatedChannel, () -> {
            RTopic topic = client.getTopic(validatedChannel);
            int listenerId = topic.addListener(validatedType,
                    (ignoredChannel, message) -> validatedConsumer.accept(message));
            return new RedisSubscription(validatedChannel, topic, listenerId);
        });
    }

    /** 使用服务端 limit 扫描 key，并返回不可变快照。 */
    public List<String> scanKeys(String pattern, int limit) {
        String validatedPattern = requireKey(pattern);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return execute("scanKeys", validatedPattern, () -> {
            RKeys keys = client.getKeys();
            List<String> result = new ArrayList<>(limit);
            KeysScanOptions options = KeysScanOptions.defaults()
                    .pattern(validatedPattern)
                    .limit(limit);
            for (String key : keys.getKeys(options)) {
                if (result.size() == limit) {
                    break;
                }
                result.add(key);
            }
            return List.copyOf(result);
        });
    }

    /** 删除匹配显式 pattern 的所有 key，并返回删除数量。 */
    public long deleteByPattern(String pattern) {
        String validatedPattern = requireKey(pattern);
        return execute("deleteByPattern", validatedPattern,
                () -> client.getKeys().deleteByPattern(validatedPattern));
    }

    private <T> T executeWithLock(String key, Supplier<T> action, LockAttempt attempt) {
        RLock lock;
        boolean acquired;
        try {
            lock = client.getLock(key);
            acquired = attempt.tryLock(lock);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RedisLockException("acquire", key, exception);
        } catch (RuntimeException exception) {
            throw new RedisLockException("acquire", key, exception);
        }
        if (!acquired) {
            throw new RedisLockException("acquire", key);
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException | Error actionFailure) {
            try {
                releaseLock(lock, key);
            } catch (RedisLockException releaseFailure) {
                actionFailure.addSuppressed(releaseFailure);
            }
            throw actionFailure;
        }
        releaseLock(lock, key);
        return result;
    }

    private static void releaseLock(RLock lock, String key) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException exception) {
            throw new RedisLockException("release", key, exception);
        }
    }

    private <T> T execute(String operation, String key, Supplier<T> action) {
        try {
            return action.get();
        } catch (RedisOperationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RedisOperationException(operation, key, exception);
        }
    }

    private void executeVoid(String operation, String key, Runnable action) {
        execute(operation, key, () -> {
            action.run();
            return null;
        });
    }

    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        return key;
    }

    private static <T> T requireValue(T value) {
        return Objects.requireNonNull(value, "value must not be null");
    }

    private static <T> Collection<? extends T> requireValues(Collection<? extends T> values) {
        Objects.requireNonNull(values, "values must not be null");
        for (T value : values) {
            requireValue(value);
        }
        return values;
    }

    private static <K, V> Map<? extends K, ? extends V> requireMap(
            Map<? extends K, ? extends V> values) {
        Objects.requireNonNull(values, "values must not be null");
        values.forEach((key, value) -> {
            Objects.requireNonNull(key, "map key must not be null");
            Objects.requireNonNull(value, "map value must not be null");
        });
        return values;
    }

    private static Duration requirePositiveDuration(String name, Duration duration) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }

    private static void requireIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
    }

    private static void requireRange(int start, int end) {
        requireIndex(start);
        if (end < start) {
            throw new IllegalArgumentException("end must be greater than or equal to start");
        }
    }

    private static <T> List<T> immutableList(Collection<?> source, Class<T> type) {
        List<T> result = new ArrayList<>(source.size());
        source.forEach(value -> result.add(type.cast(value)));
        return List.copyOf(result);
    }

    private static <T> Set<T> immutableSet(Collection<?> source, Class<T> type) {
        Set<T> result = new LinkedHashSet<>(source.size());
        source.forEach(value -> result.add(type.cast(value)));
        return Set.copyOf(result);
    }

    private static <K, V> Map<K, V> immutableMap(Map<?, ?> source,
                                                  Class<K> keyType,
                                                  Class<V> valueType) {
        Map<K, V> result = new LinkedHashMap<>(source.size());
        source.forEach((key, value) -> result.put(keyType.cast(key), valueType.cast(value)));
        return Map.copyOf(result);
    }

    @FunctionalInterface
    private interface LockAttempt {
        boolean tryLock(RLock lock) throws InterruptedException;
    }
}

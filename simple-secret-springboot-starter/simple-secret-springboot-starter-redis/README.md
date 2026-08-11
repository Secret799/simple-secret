# Simple Secret Redis Starter

`simple-secret-springboot-starter-redis` 面向 Java 17 和 Spring Boot 3.5，基于 Redisson 提供显式连接、对象与集合操作、原子计数、分布式锁、限流、发布订阅、队列和可选 Spring Cache。

模块不依赖 Honeybee、Lock4j、Caffeine、Spring Data Redis、Spring Web、Servlet、JSON starter、Hutool 或 Lombok。Spring Cache 使用 Redisson 官方 `RedissonSpringCacheManager`，因此只保留其真实运行所需的 Spring Cache 与事务 API。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-redis</artifactId>
</dependency>
```

未使用 BOM 时指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-redis</artifactId>
    <version>1.1.0</version>
</dependency>
```

## 安全默认行为

starter 默认关闭，不提供默认 Redis 地址，也不会连接 `localhost`：

```yaml
simple-secret:
  redis:
    enabled: false
```

显式启用且应用没有提供 `RedissonClient` 时，starter 创建名为 `simpleSecretRedissonClient` 的客户端，并在 Spring 容器关闭时调用 `shutdown()`。应用自行提供 `RedissonClient` 时，starter 只创建操作门面，不接管该客户端的生命周期。

配置对象和异常消息不会输出密码、业务值或 Redisson 服务端异常文本。Redis 地址禁止嵌入 userinfo，账号密码应使用独立配置项。

## 单机配置

```yaml
simple-secret:
  redis:
    enabled: true
    mode: single
    key-prefix: order-service
    threads: 16
    netty-threads: 32
    use-script-cache: true
    single:
      address: rediss://redis.example.internal:6379
      username: ${REDIS_USERNAME:}
      password: ${REDIS_PASSWORD:}
      client-name: order-service
      database: 0
      connect-timeout: 10s
      timeout: 3s
      idle-connection-timeout: 10s
      connection-minimum-idle-size: 8
      connection-pool-size: 32
      subscription-connection-minimum-idle-size: 1
      subscription-connection-pool-size: 8
```

`key-prefix: order-service` 会把 Redisson 对象名统一映射为 `order-service:<name>`。已经带有该前缀的名称不会重复添加。

## 集群配置

```yaml
simple-secret:
  redis:
    enabled: true
    mode: cluster
    key-prefix: telemetry
    cluster:
      node-addresses:
        - rediss://redis-1.example.internal:6379
        - rediss://redis-2.example.internal:6379
        - rediss://redis-3.example.internal:6379
      username: ${REDIS_USERNAME:}
      password: ${REDIS_PASSWORD:}
      client-name: telemetry-service
      scan-interval: 5s
      connect-timeout: 10s
      timeout: 3s
      idle-connection-timeout: 10s
      master-connection-minimum-idle-size: 8
      master-connection-pool-size: 32
      slave-connection-minimum-idle-size: 8
      slave-connection-pool-size: 32
      subscription-connection-minimum-idle-size: 1
      subscription-connection-pool-size: 8
      read-mode: slave
      subscription-mode: master
```

地址只接受带主机和端口的 `redis://` 或 `rediss://` URI。连接、命令、空闲和扫描时间必须为正值，连接池最小空闲数不能大于池容量。

## 对象、集合和原子计数

注入 `RedissonOperations`：

```java
import com.ss.redis.operation.RedissonOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class OrderStateStore {
    private final RedissonOperations redis;

    public OrderStateStore(RedissonOperations redis) {
        this.redis = redis;
    }

    public void save(OrderState state) {
        redis.set("orders:" + state.id(), state, Duration.ofMinutes(30));
        redis.addAllToList("recent-orders", List.of(state.id()));
        redis.putToMap("order-status", state.id(), state.status(), String.class);
        redis.incrementAtomicLong("order-write-count");
    }

    public OrderState get(String id) {
        return redis.get("orders:" + id, OrderState.class);
    }

    public Map<String, String> statuses() {
        return redis.getMap("order-status", String.class, String.class);
    }
}
```

List、Set 和 Map 读取返回不可变快照，不向业务代码暴露可变 Redisson 远程句柄。还支持 `setIfAbsent`、`setIfExists`、保留原 TTL 更新、区间读取、删除、存在判断、TTL、原子递增和递减。空批量输入不会访问 Redis。

## 分布式锁和限流

```java
import org.redisson.api.RateType;

import java.time.Duration;

OrderResult result = redis.withLock(
        "locks:order:" + orderId,
        Duration.ofSeconds(2),
        () -> createOrder(orderId));

OrderResult leased = redis.withLock(
        "locks:reconcile:" + orderId,
        Duration.ofSeconds(2),
        Duration.ofSeconds(30),
        () -> reconcile(orderId));

boolean allowed = redis.tryAcquire(
        "rate:user:" + userId,
        RateType.OVERALL,
        100,
        Duration.ofMinutes(1));
```

不传 `leaseTime` 时使用 Redisson watchdog。等待中断会恢复当前线程的中断标记；业务回调抛出的运行时异常或错误保持原样传播。锁未获取、获取失败或释放失败统一抛出 `RedisLockException`。

## 发布订阅和 Key 扫描

```java
import com.ss.redis.operation.RedisSubscription;

RedisSubscription subscription = redis.subscribe(
        "events:orders", OrderEvent.class, this::handleOrderEvent);

redis.publish("events:orders", new OrderEvent("created", "o-1001"));

// 应用组件停止时精确移除当前 listener；重复关闭是幂等的。
subscription.close();
```

建议把订阅句柄保存在组件字段中，并在 `@PreDestroy` 或生命周期停止回调中关闭。`scanKeys(pattern, limit)` 同时使用服务端 limit 和客户端上限，避免无界收集；`deleteByPattern(pattern)` 会删除所有匹配 key，只应接受可信、明确的 pattern。

## 队列

注入 `RedissonQueueOperations`：

```java
import com.ss.redis.operation.RedisQueueSubscription;
import com.ss.redis.operation.RedissonQueueOperations;

queueOperations.offer("jobs:thumbnail", new ThumbnailJob("image-1001"));
ThumbnailJob job = queueOperations.poll("jobs:thumbnail", ThumbnailJob.class);

queueOperations.offerPriority("jobs:priority", new PriorityJob(10, "job-1"));

RedisQueueSubscription subscription = queueOperations.subscribe(
        "jobs:thumbnail", this::processThumbnail);
subscription.close();
```

普通队列和优先队列 API 可继续使用。Redisson 3.52 已弃用 `RDelayedQueue` 和 `RBoundedBlockingQueue`，因此 `offerDelayed`、`removeDelayed`、`destroyDelayed`、`trySetCapacity` 及有界队列方法同步标记为 `@Deprecated`。它们只用于兼容已有系统；新系统应直接评估 Redisson `RReliableQueue` 的 delay 和 queue size limit 能力。

## Spring Cache

Spring Cache 默认关闭。启用后只创建配置中显式声明的缓存名，不允许运行时动态创建任意 cacheName：

```yaml
simple-secret:
  redis:
    enabled: true
    single:
      address: rediss://redis.example.internal:6379
      password: ${REDIS_PASSWORD:}
    cache:
      enabled: true
      allow-null-values: false
      transaction-aware: true
      caches:
        users:
          ttl: 30m
          max-idle-time: 5m
          max-size: 10000
        permissions:
          ttl: 10m
          max-idle-time: 0s
          max-size: 5000
```

```java
import org.springframework.cache.annotation.Cacheable;

@Cacheable(cacheNames = "users", key = "#userId")
public User loadUser(String userId) {
    return repository.findRequired(userId);
}
```

`ttl`、`max-idle-time` 和 `max-size` 的 `0` 表示不设置对应限制。应用已提供其他 `CacheManager` 时，starter 不创建 `redisCacheManager`。应用自行提供 `RedissonClient` 时也可以只打开 `simple-secret.redis.cache.enabled` 使用缓存功能。

## 异常语义

Redisson 运行时异常统一包装为 `RedisOperationException`，可通过 `getOperation()` 和 `getKey()` 获取稳定上下文；分布式锁使用 `RedisLockException`。异常消息不包含写入值、密码或服务端原始消息，原异常保留在 `cause` 中供受控诊断。

参数错误在访问 Redis 前抛出 `IllegalArgumentException` 或 `NullPointerException`。调用方不要把不可信 key、channel、pattern 或业务对象直接写入日志。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-redis \
  -am test
```

模块测试不需要真实 Redis，覆盖配置、自动配置、客户端所有权、数据结构、锁、限流、发布订阅、扫描、队列、Spring Cache 和敏感信息边界。

# Simple Secret Idempotent Starter

`simple-secret-springboot-starter-idempotent` 为 Spring Boot Servlet 应用提供方法级重复提交保护。模块使用
SHA-256 生成固定长度 key，通过带 TTL 和 owner token 的原子租约阻止同一请求在时间窗口内重复执行。

## Maven 依赖

推荐先导入 Simple Secret BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.ss</groupId>
            <artifactId>simple-secret-common-bom</artifactId>
            <version>1.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.ss</groupId>
        <artifactId>simple-secret-springboot-starter-idempotent</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

模块不传递 Simple Secret JSON、Redis、Core 或 Web starter，也不依赖 Sa-Token、Spring Security、
Hutool、Lombok 或业务 `Result`。Redisson 是 optional 适配依赖；使用默认 Redisson Store 时应用需要
显式引入并配置 Redisson，例如使用 Simple Secret Redis starter。

## Redisson Store

应用上下文存在 `RedissonClient` 时，starter 自动创建 `RedissonIdempotencyStore`：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-redis</artifactId>
</dependency>
```

```yaml
simple-secret:
  redis:
    enabled: true
    mode: single
    single:
      address: redis://127.0.0.1:6379
  idempotent:
    enabled: true
    key-prefix: my-app:idempotent:
    identity-header: Authorization
```

Idempotent starter 不创建或关闭 `RedissonClient`，连接生命周期由 Redis starter 或应用管理。启用
idempotent 但没有 `IdempotencyStore` 时应用启动失败，避免 `@RepeatSubmit` 被静默忽略。

## 注解使用

```java
import com.ss.idempotent.annotation.RepeatSubmit;

import java.util.concurrent.TimeUnit;

@RestController
class OrderController {

    @PostMapping("/orders")
    @RepeatSubmit(
            interval = 5,
            timeUnit = TimeUnit.SECONDS,
            message = "{order.repeat-submit}")
    OrderView create(@RequestBody CreateOrderCommand command) {
        return orderService.create(command);
    }
}
```

保护窗口不能小于 1 秒。正常返回一律保留租约到 TTL；目标方法抛异常时默认释放本次 owner 对应的
租约，使客户端可以重试。对不可重试操作可设置 `releaseOnException = false`。

`message` 使用 `{code}` 格式时通过 Spring `MessageSource` 和当前 Locale 解析；找不到消息码时保留
注解文本。应用可以在全局异常处理器中将 `RepeatSubmitException` 映射为 HTTP 409 或业务错误码。

## 自定义 Store

非 Redisson 环境可以声明自己的原子存储。`tryAcquire` 必须同时完成不存在判断、写入 owner 和设置 TTL；
`release` 必须只在 owner 匹配时删除：

```java
@Bean
IdempotencyStore idempotencyStore(AtomicLeaseRepository repository) {
    return new IdempotencyStore() {
        @Override
        public boolean tryAcquire(String key, String owner, Duration ttl) {
            return repository.insertIfAbsent(key, owner, ttl);
        }

        @Override
        public boolean release(String key, String owner) {
            return repository.deleteIfOwnerMatches(key, owner);
        }
    };
}
```

不要用“先查询、再写入”实现 `tryAcquire`，该过程存在并发竞态。也不要在 release 时无条件删除 key，
否则旧请求可能误删 TTL 过期后由新请求获取的租约。

## 自定义身份与 Key

默认身份按以下顺序解析：配置请求头、已存在的 session ID、`request.getRemoteAddr()`。默认不信任
`X-Forwarded-For`；部署在可信反向代理后，应由应用验证代理链并覆盖 resolver：

```java
@Bean
RequestIdentityResolver requestIdentityResolver(CurrentUser currentUser) {
    return request -> currentUser.requireUserId();
}
```

默认 key 包含 HTTP method、URI、Java 方法签名、身份和过滤后的业务参数。Servlet request/response、
`BindingResult`、`MultipartFile` 及其数组、集合和 Map 元素不会进入摘要。复杂 multipart DTO、流式参数、
不能被 Jackson 序列化的类型或需要业务幂等键时，应覆盖 `IdempotencyKeyGenerator`：

```java
@Bean
IdempotencyKeyGenerator idempotencyKeyGenerator() {
    return (method, args, request) -> "my-app:idempotent:order:" +
            sha256(((CreateOrderCommand) args[0]).requestId());
}
```

自定义 generator 仍不得把 token、请求体或个人数据原文写入 Redis key。

## 安全与功能边界

- Spring AOP 只拦截经过代理的 Spring Bean 调用；同类自调用、非 Spring 对象以及 private/final 方法
  不受保护。
- Redis 或 key 序列化异常会失败关闭并阻止请求，不会绕过幂等保护。
- 远端地址回退可能使 NAT 后相同参数的匿名用户共享窗口；公开匿名接口应提供更可靠的 resolver 或显式
  业务幂等键。
- 本功能只降低短时间重复执行风险，不替代数据库唯一约束、事务、业务请求号、消息消费幂等或跨服务
  一致性设计。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-idempotent \
  clean verify
```

测试覆盖稳定 key、敏感原文隔离、multipart 过滤、owner-safe Redisson 租约、重复拒绝、异常释放、
国际化消息、自动配置开关、消费者 Bean 覆盖、缺 Store 启动失败和发布依赖边界。

# Simple Secret Core Starter

`simple-secret-springboot-starter-core` 为 Java 17、Spring Boot 3.5 应用提供通用响应与异常类型、项目元数据，以及显式开启的任务执行器、调度器、`@Async` 和 Bean Validation fail-fast 配置。

纯 Java API 位于 `simple-secret-common-core`，没有任何生产依赖。starter 不依赖 Spring Web、Servlet、JSON、Hutool、Lombok、TTL 或业务模块。添加 starter 后所有线程和全局框架行为默认关闭。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-core</artifactId>
</dependency>
```

普通 Java 项目只需要结果和异常类型时，直接使用零第三方依赖模块：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-common-core</artifactId>
</dependency>
```

未使用 BOM 时为依赖增加 `<version>1.1.0</version>`。

## 默认行为

仅添加依赖时，starter 只发布 `CoreProperties`，不会创建执行器、调度器、异步代理或 Validator：

```yaml
simple-secret:
  core:
    task-executor:
      enabled: false
    scheduler:
      enabled: false
    async:
      enabled: false
    validation:
      fail-fast: false
```

项目元数据可以独立配置，不会触发其他功能：

```yaml
simple-secret:
  core:
    project:
      name: order-service
      description: Order processing service
      version: 1.0.0
      copyright-year: 2026
```

```java
import com.ss.core.config.CoreProperties;
import org.springframework.stereotype.Component;

@Component
public class ProjectInfo {
    private final CoreProperties properties;

    public ProjectInfo(CoreProperties properties) {
        this.properties = properties;
    }

    public String displayName() {
        return properties.getProject().getName()
                + " " + properties.getProject().getVersion();
    }
}
```

## Result API

`Result<T>` 是普通可序列化 JavaBean，不依赖 JSON 框架：

```java
import com.ss.core.domain.Result;

Result<Order> created = Result.ok("created", order);
Result<String> token = Result.ok("token-value");
Result<Void> rejected = Result.failMessage("order rejected");
Result<Void> invalid = Result.fail(422, "invalid order");
Result<Order> warning = Result.warn("manual review required", order);

if (Result.isError(created)) {
    throw new IllegalStateException(created.getMessage());
}
```

字符串可以直接作为 data 使用。自定义消息使用 `okMessage`、`failMessage` 或消息加 data 的双参数工厂，避免字符串重载歧义。`isSuccess(null)` 返回 `false`，`isError(null)` 返回 `true`。

## 异常 API

```java
import com.ss.core.exception.BusinessException;
import com.ss.core.exception.ServiceException;

throw BusinessException.normalForModule(
        "orders", "order {} is already closed", orderId);
```

待上层国际化的错误可以只保存 code 和参数：

```java
BusinessException exception = BusinessException.i18nForModule(
        "orders", "order.not-found", orderId);

String code = exception.getCode();
Object[] arguments = exception.getArguments();
```

pure core 不访问 Spring `MessageSource`。Web 层应在自己的异常处理器中根据 code 和 arguments 解析本地化消息。

保留下游原因和内部诊断详情：

```java
throw new ServiceException("payment request failed", cause)
        .setDetailMessage("provider connection timeout");
```

不要把 `detailMessage` 直接返回给客户端；它只适合受控日志和诊断。

## 任务执行器

显式开启后创建名为 `simpleSecretTaskExecutor` 的 `ThreadPoolTaskExecutor`：

```yaml
simple-secret:
  core:
    task-executor:
      enabled: true
      core-pool-size: 4
      max-pool-size: 8
      queue-capacity: 256
      keep-alive: 60s
      thread-name-prefix: order-task-
```

```java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class ReconcileService {
    private final Executor executor;

    public ReconcileService(
            @Qualifier("simpleSecretTaskExecutor") Executor executor) {
        this.executor = executor;
    }

    public void submit(String orderId) {
        executor.execute(() -> reconcile(orderId));
    }
}
```

核心线程数必须大于 0，最大线程数不能小于核心线程数，队列容量不能为负，keep-alive 不能为负，线程名前缀不能为空。拒绝策略为 `CallerRunsPolicy`。Spring 容器关闭时会等待已提交任务，最长 30 秒。

## 调度器

调度器与任务执行器互相独立。显式开启后创建名为 `simpleSecretScheduledExecutorService` 的 `ScheduledExecutorService`：

```yaml
simple-secret:
  core:
    scheduler:
      enabled: true
      pool-size: 2
      thread-name-prefix: order-schedule-
```

```java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class CleanupSchedule {
    public CleanupSchedule(
            @Qualifier("simpleSecretScheduledExecutorService")
            ScheduledExecutorService scheduler) {
        scheduler.scheduleWithFixedDelay(
                this::cleanup, 0, Duration.ofMinutes(5).toSeconds(), TimeUnit.SECONDS);
    }
}
```

任务抛出的异常仍保存在返回的 `Future` 中，并由调度器使用 JDK System Logger 记录。容器关闭时调度器调用 `shutdown()`。

## Spring Async

`@Async` 支持默认关闭。启用前必须开启 starter 任务执行器，或者由应用提供名为 `simpleSecretTaskExecutor` 的 `Executor`：

```yaml
simple-secret:
  core:
    task-executor:
      enabled: true
      core-pool-size: 4
      max-pool-size: 8
    async:
      enabled: true
```

```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ReportService {
    @Async
    public CompletableFuture<Report> generate(String reportId) {
        return CompletableFuture.completedFuture(buildReport(reportId));
    }
}
```

异步 `void` 方法的未捕获异常只记录方法签名，不记录参数值，也不会在线程池线程中重新包装抛出。应用声明自己的 `AsyncConfigurer` 时 starter 完全让位。启用 async 但既没有消费者 `AsyncConfigurer`，也没有指定名称的执行器时，应用会在启动阶段失败。

## Fail-fast Validation

Validation API 和 Hibernate Validator 在 starter 中为 optional，不会传递给普通消费者。使用该功能时由应用显式引入标准 Boot starter：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

```yaml
simple-secret:
  core:
    validation:
      fail-fast: true
```

启用后，starter 只在应用没有 `jakarta.validation.Validator` Bean 时创建 `LocalValidatorFactoryBean`，并设置 Hibernate Validator fail-fast。它使用 `ParameterMessageInterpolator`，不要求额外 Jakarta EL 实现。需要 Spring `MessageSource` 深度集成时，应用应声明自己的 Validator Bean，starter 会自动让位。

## 覆盖默认 Bean

应用可以使用公开 Bean 名接管线程池：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

@Configuration(proxyBeanMethods = false)
public class ExecutorConfiguration {
    @Bean(name = "simpleSecretTaskExecutor")
    Executor taskExecutor() {
        return command -> new Thread(command, "application-task").start();
    }
}
```

消费者 Bean 的生命周期由消费者负责。starter 只关闭自己创建的执行器和调度器。

## 依赖边界

- `simple-secret-common-core` 没有生产依赖。
- core starter 不依赖 Spring Web、Servlet、JSON、Hutool、Lombok 或 TTL。
- `jakarta.validation-api` 和 `hibernate-validator` 为 optional，不会进入未启用消费者的 classpath。
- Spring `@Async` 基于 Spring Context 自带的异步基础设施，不额外引入 `spring-boot-starter-aop`。
- 不提供缓存、租户、用户、登录或 Redis key 等业务常量。
- 不提供基于正则的 XSS 校验；Web 应用应采用上下文输出编码、CSP 和经过审计的内容净化方案。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-core \
  -am test
```

独立消费者测试位于 `integration-tests/consumer-core`，验证 BOM 接入、默认零副作用、optional 依赖缺失、执行器生命周期和消费者 Bean 覆盖。

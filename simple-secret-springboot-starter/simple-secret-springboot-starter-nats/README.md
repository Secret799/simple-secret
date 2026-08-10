# Simple Secret NATS Starter

`simple-secret-springboot-starter-nats` 为 Java 17 和 Spring Boot 3.5 提供 NATS 多客户端、发布、请求响应、普通订阅、queue group 订阅和 Spring Bean 自动订阅。

模块遵循最小依赖原则，生产依赖只有 JNATS、SLF4J API 和 Spring Boot 自动配置。不依赖 JSON、Jackson、Hutool、Fastjson、Lombok、Toolbox 或其他 Simple Secret starter。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-nats</artifactId>
</dependency>
```

不使用 BOM 时显式指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-nats</artifactId>
    <version>1.1.0</version>
</dependency>
```

## 最小配置

starter 默认加载自动配置，但没有默认服务器、用户名或密码，也不会隐式创建 `default` 连接。只有客户端显式配置 `enabled: true` 才会联网：

```yaml
simple-secret:
  nats:
    enabled: true
    clients:
      edge:
        enabled: true
        url: nats://localhost:4222
        connection-name: edge-service
        reconnect-enabled: true
        max-reconnects: -1
        reconnect-wait-millis: 5000
        reconnect-jitter-millis: 2000
        connection-timeout-millis: 10000
        publish-timeout-millis: 10000
        request-timeout-millis: 30000
```

账号密码应通过环境变量或密钥系统注入：

```yaml
simple-secret:
  nats:
    clients:
      edge:
        username: ${NATS_USERNAME}
        password: ${NATS_PASSWORD}
```

设置了密码却没有用户名会在启动时失败，不会静默忽略凭据。设置 `simple-secret.nats.enabled=false` 可完全关闭自动配置。

## 接收消息

将 `NatsMessageHandler` 实现声明为 Spring Bean。`clientKey()` 必须匹配配置中的客户端键；默认值是 `default`，但 starter 不会自动创建该客户端：

```java
import com.ss.nats.handler.NatsMessageHandler;
import com.ss.nats.message.NatsMessageContext;
import org.springframework.stereotype.Component;

@Component
public class DeviceStateHandler implements NatsMessageHandler {
    @Override
    public String clientKey() {
        return "edge";
    }

    @Override
    public String subject() {
        return "devices.*.state";
    }

    @Override
    public void handle(NatsMessageContext message) {
        String actualSubject = message.getSubject();
        String payload = message.getPayloadAsString();
        // 处理消息；日志中不要直接输出不可信 payload。
    }
}
```

默认处理器提交到有界 handler executor。需要严格按 Dispatcher 到达顺序处理时返回 `true`：

```java
@Override
public boolean ordered() {
    return true;
}
```

有序处理器会占用 JNATS Dispatcher 回调线程，不应执行长时间阻塞操作。

## Queue Group 订阅

`queue()` 默认为空，表示普通订阅，每个实例都会收到消息。只有业务显式返回 queue group 时才启用负载均衡：

```java
@Component
public class TelemetryWorker implements NatsMessageHandler {
    @Override
    public String clientKey() {
        return "edge";
    }

    @Override
    public String subject() {
        return "devices.*.telemetry";
    }

    @Override
    public String queue() {
        return "telemetry-workers";
    }

    @Override
    public void handle(NatsMessageContext message) {
        byte[] payload = message.getPayload();
    }
}
```

空 queue 的安全默认值避免多个不同业务处理器被意外放进同一个默认队列。

## 消息校验

同一个处理器同时实现 `NatsMessageValidator`，校验会在 `handle` 之前执行。返回 `false` 时只丢弃当前消息：

```java
import com.ss.nats.handler.NatsMessageValidator;

@Component
public class SignedCommandHandler
        implements NatsMessageHandler, NatsMessageValidator {
    @Override
    public String clientKey() {
        return "edge";
    }

    @Override
    public String subject() {
        return "commands.*";
    }

    @Override
    public boolean validate(NatsMessageContext message) {
        return message.getHeaders().getFirst("X-Signature") != null;
    }

    @Override
    public void handle(NatsMessageContext message) {
        // 只处理通过校验的消息。
    }
}
```

## 发布消息

注入 `NatsClientManager` 后可发布 UTF-8 文本、字节数组或调用方构造的 JNATS `Message`：

```java
import com.ss.nats.client.NatsClientManager;
import org.springframework.stereotype.Service;

@Service
public class DeviceCommandPublisher {
    private final NatsClientManager nats;

    public DeviceCommandPublisher(NatsClientManager nats) {
        this.nats = nats;
    }

    public void restart(String deviceId) {
        nats.publish("edge", "devices." + deviceId + ".commands", "restart");
    }
}
```

`publish` 会在有界 publish executor 中执行，并等待 `flush(publishTimeout)` 完成。客户端不存在、未连接、线程池饱和、flush 超时或发布失败时统一抛出 `NatsOperationException`，不会返回含义模糊的布尔值。

## 请求响应

```java
import com.ss.nats.message.NatsMessageContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

Optional<NatsMessageContext> response = nats.request(
        "edge",
        "devices.device-1.status.request",
        "{}".getBytes(StandardCharsets.UTF_8),
        Duration.ofSeconds(2));

String status = response
        .map(NatsMessageContext::getPayloadAsString)
        .orElseThrow(() -> new IllegalStateException("NATS request timed out"));
```

超时或没有响应时返回 `Optional.empty()`。线程中断会恢复中断标记并抛出 `NatsOperationException`。省略显式 `Duration` 时使用客户端的 `request-timeout-millis`。

## JSON 解码

NATS starter 不依赖任何 JSON 实现。`NatsMessageContext` 提供防御性复制的字节、UTF-8 文本和调用方解码函数：

```java
DeviceState state = message.decode(bytes -> customCodec.decode(bytes, DeviceState.class));
DeviceState textState = message.decodeText(jsonCodec::parseDeviceState);
```

应用同时使用 Simple Secret JSON starter 时，显式声明该依赖并注入 `JsonCodec`：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-json</artifactId>
</dependency>
```

```java
@Component
public class JsonDeviceStateHandler implements NatsMessageHandler {
    private final JsonCodec jsonCodec;

    public JsonDeviceStateHandler(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    @Override
    public String clientKey() {
        return "edge";
    }

    @Override
    public String subject() {
        return "devices.*.state";
    }

    @Override
    public void handle(NatsMessageContext message) {
        DeviceState state = message.decodeText(
                json -> jsonCodec.parseObject(json, DeviceState.class));
    }
}
```

## 非 Spring 使用

核心管理器可在普通 Java 程序中直接创建。调用方负责线程池生命周期：

```java
import com.ss.nats.client.NatsClientManager;
import com.ss.nats.config.NatsClientOptions;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

ExecutorService publisher = Executors.newFixedThreadPool(2);
ExecutorService handlers = Executors.newFixedThreadPool(4);
NatsClientManager manager = new NatsClientManager(publisher, handlers);

NatsClientOptions options = new NatsClientOptions();
options.setEnabled(true);
options.setUrl("nats://localhost:4222");

try {
    manager.refreshClients(Map.of("edge", options), (clientKey, current) -> { });
    manager.publish("edge", "demo.events", "hello");
} finally {
    manager.close();
    publisher.shutdown();
    handlers.shutdown();
}
```

`NatsClientManager.close()` 会先关闭所有 Dispatcher，再等待连接 drain，最后关闭连接；它不会关闭调用方传入的执行器。

## Subject 与安全约束

- 发布 subject 不允许通配符，订阅 subject 只允许完整 token 的 `*` 和末尾 `>`。
- subject 和 queue 不允许空 token、空白字符或非法通配符。
- 消息上下文是不可变快照，payload 与 headers getter 均返回防御性副本。
- 连接配置变化时才重建连接；调用方随后修改原配置对象不会篡改已保存的配置快照。
- 日志和统一异常只记录 `clientKey`、subject、操作名和处理器类型，不输出 payload、用户名或密码。
- 发布和异步处理线程池均为有界队列，饱和时明确拒绝，不在调用线程静默执行。
- 生产环境应使用 TLS、最小权限账号和外部密钥注入，不应把凭据写进仓库。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-nats test
```

模块测试不依赖真实 NATS 服务，覆盖自动配置、配置模型、subject 校验、多客户端生命周期、发布、请求响应、处理器订阅、刷新和依赖边界。

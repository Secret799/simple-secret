# Simple Secret MQTT v5 Starter

`simple-secret-springboot-starter-mqttv5` 面向 Java 17 和 Spring Boot 3.5，提供 MQTT v5 多客户端连接、发布订阅、共享订阅、同步请求与重试、断线重连、Spring Bean 处理器发现和配置刷新。

模块按业务能力显式依赖 JSON starter，因此 `MqttMessageContext` 可以直接把 JSON payload 转为对象。除此之外不依赖 Honeybee、Hutool、Guava 或 Lombok。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-mqttv5</artifactId>
</dependency>
```

未使用 BOM 时指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-mqttv5</artifactId>
    <version>1.1.0</version>
</dependency>
```

## 最小配置

只有 `clients` 中显式设置 `enabled: true` 的客户端会建立连接。starter 不提供默认 broker、用户名或密码：

```yaml
simple-secret:
  mqtt:
    enabled: true
    clients:
      default:
        enabled: true
        broker: tcp://localhost:1883
        client-id: simple-secret-demo
        clean-start: true
        keep-alive-seconds: 30
        connection-timeout-seconds: 10
        publish-timeout-seconds: 10
        reconnect-enabled: true
        reconnect-delay-millis: 1000
```

账号密码应通过环境变量或密钥系统注入：

```yaml
simple-secret:
  mqtt:
    clients:
      default:
        username: ${MQTT_USERNAME:}
        password: ${MQTT_PASSWORD:}
```

需要持久会话时应使用稳定且唯一的 `client-id`，并按 broker 语义设置 `clean-start: false`。设置 `simple-secret.mqtt.enabled=false` 可完全关闭自动配置。

## 接收消息

将 `MqttMessageHandler` 实现声明为 Spring Bean。starter 会在客户端连接和重连成功后恢复订阅：

```java
import com.ss.mqttv5.handler.MqttMessageHandler;
import com.ss.mqttv5.message.MqttMessageContext;
import org.springframework.stereotype.Component;

@Component
public class DeviceStateHandler implements MqttMessageHandler {
    @Override
    public String topic() {
        return "devices/+/state";
    }

    @Override
    public int qos() {
        return 1;
    }

    @Override
    public void handle(MqttMessageContext message) {
        DeviceState state = message.getPayload(DeviceState.class);
        String actualTopic = message.getTopic();
        // 持久化状态；不要把不可信 payload 原样写入日志。
    }
}
```

`MqttMessageContext` 是消息快照，支持 `getPayloadAsString()`、`getPayload(Class<T>)`、`getPayload(TypeReference<T>)`，并提供实际 topic、订阅 filter、client key、QoS、retained 标记和 MQTT v5 properties。

## 多客户端和共享订阅

处理器通过 `clientKey()` 选择客户端，通过 `shareGroup()` 创建 MQTT v5 共享订阅：

```java
@Component
public class SharedTelemetryHandler implements MqttMessageHandler {
    @Override
    public String clientKey() {
        return "telemetry";
    }

    @Override
    public String topic() {
        return "devices/+/telemetry";
    }

    @Override
    public String shareGroup() {
        return "telemetry-workers";
    }

    @Override
    public void handle(MqttMessageContext message) {
        String payload = message.getPayloadAsString();
    }
}
```

`clientKey()` 必须对应配置中的客户端键。共享组和 topic filter 会统一校验，不合法时在订阅前失败。

## 发布消息

```java
import com.ss.json.JsonCodec;
import com.ss.mqttv5.client.MqttClientManager;
import org.springframework.stereotype.Service;

@Service
public class DeviceCommandPublisher {
    private final MqttClientManager mqtt;
    private final JsonCodec jsonCodec;

    public DeviceCommandPublisher(MqttClientManager mqtt, JsonCodec jsonCodec) {
        this.mqtt = mqtt;
        this.jsonCodec = jsonCodec;
    }

    public void reboot(String deviceId) {
        String payload = jsonCodec.toJsonString(
                new DeviceCommand("reboot", System.currentTimeMillis()));
        mqtt.publish("default", "devices/" + deviceId + "/commands", payload, 1);
    }
}
```

发布前可使用 `mqtt.isConnected("default")` 判断连接状态。topic、QoS、client key 或连接状态不合法时会快速失败；发布异常统一包装为 `MqttOperationException`。

## 请求响应和重试

请求正文与响应正文必须能提取相同且唯一的关联键：

```java
import com.ss.json.utils.JsonUtils;
import com.ss.mqttv5.message.MqttMessageContext;
import com.ss.mqttv5.waiter.MqttCorrelationExtractor;

import java.time.Duration;
import java.util.Optional;

String requestJson = "{\"requestId\":\"req-1001\",\"action\":\"status\"}";
MqttCorrelationExtractor extractor = (type, payload) ->
        String.valueOf(JsonUtils.parseMap(payload).get("requestId"));

Optional<MqttMessageContext> response = mqtt.requestWithRetry(
        "default",
        "devices/device-01/requests",
        "devices/device-01/replies",
        requestJson,
        1,
        Duration.ofSeconds(3),
        extractor,
        3);

DeviceReply reply = response
        .map(message -> message.getPayload(DeviceReply.class))
        .orElseThrow(() -> new IllegalStateException("MQTT request timed out"));
```

`attempts` 表示总发布次数，不是额外重试次数。等待器会临时订阅 reply filter，并在完成、超时或异常后释放。关联键必须唯一，避免并发请求互相覆盖。

## 非 Spring 使用

核心管理器可以在普通 Java 程序中直接创建。调用方负责线程池生命周期：

```java
import com.ss.mqttv5.client.MqttClientManager;
import com.ss.mqttv5.config.MqttClientOptions;
import com.ss.mqttv5.waiter.DefaultMqttResponseWaiter;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

ExecutorService publisher = Executors.newFixedThreadPool(2);
ExecutorService handlers = Executors.newFixedThreadPool(4);
ScheduledExecutorService connections = Executors.newScheduledThreadPool(1);

MqttClientManager manager = new MqttClientManager(
        publisher, handlers, connections, new DefaultMqttResponseWaiter());
MqttClientOptions options = new MqttClientOptions();
options.setEnabled(true);
options.setBroker("tcp://localhost:1883");
options.setClientId("standalone-client");

CountDownLatch connected = new CountDownLatch(1);
manager.refreshClients(
        Map.of("default", options),
        (clientKey, current) -> connected.countDown());
if (!connected.await(10, TimeUnit.SECONDS)) {
    throw new IllegalStateException("MQTT connection timed out");
}
manager.publish("default", "demo/events", "{\"ok\":true}", 1);

manager.close();
publisher.shutdown();
handlers.shutdown();
connections.shutdown();
```

`MqttClientManager.close()` 只关闭 MQTT 客户端，不关闭调用方传入的线程池。自动配置创建的线程池由 Spring 销毁。

若运行环境存在 Spring Cloud，starter 会通过反射识别 `EnvironmentChangeEvent`，只有 `simple-secret.mqtt` 前缀下的键变化时才刷新客户端；starter 本身不直接依赖 Spring Cloud。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-mqttv5 test
```

模块测试不依赖真实 broker，覆盖配置、自动配置、连接生命周期、发布、订阅、请求响应、重试和依赖边界。

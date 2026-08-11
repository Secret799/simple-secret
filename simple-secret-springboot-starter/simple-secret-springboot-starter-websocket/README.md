# Simple Secret WebSocket Starter

`simple-secret-springboot-starter-websocket` 为 Servlet 应用提供 WebSocket 端点注册、本机会话管理、
定向发送、广播、认证扩展和可选跨节点消息桥接。模块默认关闭，不创建公开端点、认证体系、Redis 连接
或 Servlet 容器。

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
        <artifactId>simple-secret-springboot-starter-websocket</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

本 starter 提供 Spring MVC/WebSocket 与 Jakarta WebSocket 标准 API，但不选择 Tomcat、Jetty 或 Undertow。
应用应通过 `spring-boot-starter-web` 或自己的 Servlet 容器依赖提供服务器实现。

模块不依赖 Simple Secret Auth、Redis、JSON、Core 或 Web starter，也不依赖 Sa-Token、Hutool、Lombok、
Jackson、Redisson。认证和跨节点传输只通过 SPI 接入。

## 启用配置

```yaml
simple-secret:
  websocket:
    enabled: true
    paths:
      - /events
    send-time-limit: 10s
    send-buffer-size: 524288
```

`paths` 为空时注册应用声明的全部 handler；非空时作为允许列表。列表中的路径没有对应 handler、
handler 路径重复、路径不以 `/` 开头，应用都会启动失败。模块默认保留 Spring 同源策略，不会隐式使用
`*`。确需跨域时显式配置：

```yaml
simple-secret:
  websocket:
    allowed-origins:
      - https://console.example.com
```

生产环境不建议设置 `*`，应只允许实际前端来源，并在反向代理层同步校验 Origin。

## 匿名端点

```java
import com.ss.websocket.handler.AbstractAnonymousWebSocketHandler;
import com.ss.websocket.message.WebSocketMessenger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Configuration(proxyBeanMethods = false)
class PublicEventsWebSocketConfiguration {

    @Bean
    AbstractAnonymousWebSocketHandler publicEventsHandler(WebSocketMessenger messenger) {
        return new AbstractAnonymousWebSocketHandler("/events") {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messenger.broadcast(path(), message.getPayload());
            }
        };
    }
}
```

匿名端点不调用认证 SPI。任何公开端点都应自行限制消息大小、频率和业务指令，不能把“允许握手”视为
“允许执行任意操作”。

## 认证端点

认证端点必须同时提供 `WebSocketHandshakeAuthenticator`。认证器返回空值或抛出运行时异常时拒绝握手，
异常文本不会写入客户端响应或 starter 日志。

```java
import com.ss.websocket.auth.WebSocketHandshakeAuthenticator;
import com.ss.websocket.session.WebSocketPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;

import java.util.Map;
import java.util.Optional;

@Bean
WebSocketHandshakeAuthenticator webSocketAuthenticator(TokenService tokens) {
    return request -> {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return tokens.authenticateBearer(authorization)
                .map(user -> new WebSocketPrincipal(
                        String.valueOf(user.id()), user.username(), Map.of("tenantId", user.tenantId())));
    };
}
```

认证 handler 自动保存同一 session key 的全部连接，断开时按真实 session ID 精确删除，不会因旧标签页关闭
而误删后来建立的新连接：

```java
import com.ss.websocket.handler.AbstractAuthenticatedWebSocketHandler;
import com.ss.websocket.session.WebSocketPrincipal;
import com.ss.websocket.session.WebSocketSessionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Bean
AbstractAuthenticatedWebSocketHandler privateEventsHandler(
        WebSocketSessionRegistry registry) {
    return new AbstractAuthenticatedWebSocketHandler("/private-events", registry) {
        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            WebSocketPrincipal current = principal(session);
            // 根据 current.sessionKey() 和 attributes 执行业务校验。
        }
    };
}
```

## 本地发送

```java
import com.ss.websocket.message.WebSocketMessenger;

int connections = messenger.send("/private-events", "user-42", "refresh");
int broadcastConnections = messenger.broadcast("/events", "maintenance");
```

返回值是当前应用实例成功写入的连接数量。关闭连接不计数；I/O 写入失败抛出
`WebSocketDeliveryException`，异常不包含消息正文。注册表内部使用
`ConcurrentWebSocketSessionDecorator`，避免多个业务线程同时写同一连接。

## 跨节点消息

应用提供一个 `WebSocketMessageBroker` Bean 后，starter 会创建 `WebSocketBrokerBridge`。业务需要同时覆盖
本地和其他节点时调用 bridge；它先本地投递，再向 Broker 发布完整目标集合。来源节点会忽略自己发布的
消息，其他节点仍可向同一用户在当地保存的全部连接发送。

使用 Simple Secret Redis starter 的应用可以在应用侧完成适配，本 WebSocket starter 不产生 Redis 依赖：

```java
import com.ss.redis.operation.RedissonOperations;
import com.ss.websocket.broker.WebSocketBrokerMessage;
import com.ss.websocket.broker.WebSocketMessageBroker;
import org.springframework.context.annotation.Bean;

import java.util.function.Consumer;

@Bean
WebSocketMessageBroker webSocketMessageBroker(RedissonOperations redis) {
    return new WebSocketMessageBroker() {
        private static final String CHANNEL = "global:websocket";

        @Override
        public void publish(WebSocketBrokerMessage message) {
            redis.publish(CHANNEL, message);
        }

        @Override
        public AutoCloseable subscribe(Consumer<WebSocketBrokerMessage> consumer) {
            return redis.subscribe(CHANNEL, WebSocketBrokerMessage.class, consumer);
        }
    };
}
```

```java
import com.ss.websocket.broker.WebSocketBrokerBridge;

bridge.send("/private-events", "user-42", "refresh");
bridge.broadcast("/events", "maintenance");
```

`simple-secret.websocket.node-id` 默认随机生成 UUID；在容器镜像中可以显式设置为实例 ID，但同一运行集群
内必须唯一。Broker 必须保证消息能够序列化、订阅句柄可精确关闭，并自行处理访问控制、TLS、重连与容量。

## 安全边界

- starter 只保护握手身份和会话映射，不替代业务消息授权。
- 不记录 token、消息正文或认证异常详情。
- 默认关闭且不提供默认 echo 端点，避免仅引入依赖就扩大攻击面。
- session key 不是秘密；不要把 token 直接作为 session key。
- WebSocket 长连接仍需在代理、容器和应用层配置空闲超时、最大帧、连接数和速率限制。
- 跨节点 Broker 是应用责任；没有 Broker 时功能明确退化为单节点发送，不会静默创建 Redis 连接。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-websocket \
  clean verify
```

独立消费者验证：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -f integration-tests/pom.xml -pl consumer-websocket test
```

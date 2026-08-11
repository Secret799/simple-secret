# Simple Secret Netty WebSocket Starter

`simple-secret-springboot-starter-netty-websocket` 为 Spring Boot 应用提供独立端口的原生 Netty
WebSocket 服务，适合不希望依赖 Servlet 容器、或需要把长连接端口与 HTTP 业务端口隔离的场景。

它与 `simple-secret-springboot-starter-websocket` 不重复：后者复用 Servlet 容器和 Spring WebSocket；
本模块自行管理 Netty listener、event loop、握手、frame 和 channel。模块默认关闭，不会因仅引入依赖就
开放端口。

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
        <artifactId>simple-secret-springboot-starter-netty-websocket</artifactId>
    </dependency>
</dependencies>
```

模块只声明实际使用的 Netty component 和 Spring Boot 自动配置依赖，不使用 `netty-all`，也不依赖
Servlet WebSocket、JSON、Core、Toolbox、Redis、MQTT、Hutool、Lombok 或 Honeybee。

## 最小配置

每个端点都必须显式声明路径。端点默认要求认证，因此公开端点必须显式设置
`authentication-required=false`：

```yaml
simple-secret:
  netty:
    websocket:
      enabled: true
      host: 127.0.0.1
      port: 9839
      endpoints:
        public-events:
          path: /events
          authentication-required: false
```

`enabled` 默认 `false`。启用后 `host` 默认 `127.0.0.1`，`port` 默认 `0`，即只监听回环地址并由操作系统
分配随机端口，不会默认公开固定端口。生产环境应显式设置地址和端口；监听 `0.0.0.0` 时应通过防火墙、
TLS 反向代理和连接限流限制暴露面。

## 文本消息处理

一个已配置端点最多声明一个 `NettyWebSocketMessageHandler`。handler 的 `path()` 必须与配置完全一致：

```java
import com.ss.netty.handler.NettyWebSocketMessageHandler;
import com.ss.netty.message.NettyWebSocketMessage;
import org.springframework.context.annotation.Bean;

@Bean
NettyWebSocketMessageHandler publicEventsHandler() {
    return new NettyWebSocketMessageHandler() {
        @Override
        public String path() {
            return "/events";
        }

        @Override
        public void handle(NettyWebSocketMessage message) {
            String sessionId = message.sessionId();
            String text = message.payload();
            // 校验消息类型、权限和业务参数后再执行命令。
        }
    };
}
```

没有 handler 的端点可用于纯服务端推送；客户端向该端点发送文本时，连接会以 1008 关闭，不会静默丢弃。
模块只接受文本 frame，binary frame 返回 1003。分片 frame 会在最大载荷限制内聚合后再交给 handler。
同一连接的文本消息按到达顺序串行调用 handler，不会并发进入同一个 handler；不同连接之间可由共享线程池并行处理。

## 认证端点

认证端点需要一个 `NettyWebSocketAuthenticator` Bean。认证器收到不可变握手快照，应用可从 header、
query parameter 或 cookie 中读取凭据；推荐使用 `Authorization` header，避免 token 出现在 URL 和代理日志：

```java
import com.ss.netty.auth.NettyWebSocketAuthenticator;
import com.ss.netty.auth.NettyWebSocketPrincipal;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@Bean
NettyWebSocketAuthenticator nettyWebSocketAuthenticator(TokenService tokens) {
    return request -> request.firstHeader("authorization")
            .flatMap(tokens::authenticateBearer)
            .map(user -> new NettyWebSocketPrincipal(
                    String.valueOf(user.id()),
                    user.username(),
                    Map.of("tenantId", user.tenantId())));
}
```

返回空值或抛出运行时异常都会拒绝握手。starter 不记录认证异常详情、header、cookie、query 或消息正文。
`sessionKey` 用于聚合同一身份的多条连接，不应直接使用 token。

认证器在有界 handler 执行器中运行，不会阻塞 Netty I/O event loop。`handshake-timeout` 从连接激活开始计时，
覆盖等待完整 HTTP 请求、Origin 校验、认证和 101 响应；超时后连接关闭。认证器访问数据库或远程服务时仍应设置
自身调用超时，因为连接关闭不会强制中断已经执行中的业务认证代码。

认证 handler 可读取当前身份：

```java
NettyWebSocketPrincipal principal = message.principal().orElseThrow();
String tenantId = String.valueOf(principal.attributes().get("tenantId"));
```

握手认证只证明连接身份，不替代每条业务消息的权限校验。

## 按需组合 JSON

本模块不强制 JSON 依赖。应用确实使用 JSON 消息时再显式引入 JSON starter：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-json</artifactId>
</dependency>
```

```java
import com.ss.json.utils.JsonUtils;

DeviceCommand command = JsonUtils.parseObject(message.payload(), DeviceCommand.class);
```

这种组合不会让不使用 JSON 的 WebSocket 应用承担 Jackson 依赖，也不会让 JSON 模块反向依赖 Netty。

## 服务端推送与统计

`NettyWebSocketChannelRegistry` 支持同一身份多端连接：

```java
import com.ss.netty.session.NettyWebSocketChannelRegistry;

int pathConnections = channels.sendToPath("/events", "maintenance");
boolean sessionQueued = channels.sendToSession(sessionId, "refresh");
int userConnections = channels.sendToPrincipal("/private-events", "user-42", "refresh");
int allUserConnections = channels.sendToPrincipalAllPaths("user-42", "logout");

int online = channels.totalCount();
int eventConnections = channels.countByPath("/events");
```

返回值表示消息已向多少条当前活动连接提交写入，不表示对端已经处理或确认。连接关闭时按真实 Netty
channel ID 自动精确清理，不会因同一用户旧连接关闭而移除新连接。

## Origin、大小和容量限制

默认限制如下：

```yaml
simple-secret:
  netty:
    websocket:
      max-http-content-length: 65536
      max-frame-payload-length: 65536
      handshake-timeout: 10s
      shutdown-timeout: 5s
      handler-core-size: 2
      handler-max-size: 8
      handler-queue-capacity: 1024
```

浏览器请求携带 `Origin` 时，端点未配置白名单则要求 Origin 与 Host 同源。跨域必须按端点精确声明：

```yaml
simple-secret:
  netty:
    websocket:
      endpoints:
        private-events:
          path: /private-events
          allowed-origins:
            - https://console.example.com
```

不支持隐式 `*`。没有 `Origin` 的设备或原生客户端可继续握手，但仍必须通过端点认证。
共享执行器按 `handler-core-size`、`handler-queue-capacity`、`handler-max-size` 的顺序扩容：核心线程
占满后先进入执行器队列，队列占满后再增加线程，最多并发执行 `handler-max-size` 个任务。跨连接共享的
在途消息总上限为 `handler-max-size + handler-queue-capacity`，两项之和不得超过 `Integer.MAX_VALUE`。
总容量耗尽或执行器拒绝任务时，对应连接以 1013 关闭，避免每连接串行队列无限积压导致内存耗尽。

## 生命周期

服务实现 Spring `SmartLifecycle`。默认随容器启动同步 bind，端口占用或地址错误会直接使应用启动失败；
容器关闭时先关闭 listener，再优雅停止 boss/worker event loop。

测试或需要手动控制时可只装配不自动监听：

```yaml
simple-secret:
  netty:
    websocket:
      enabled: true
      auto-startup: false
```

```java
server.start();
int actualPort = server.localAddress().orElseThrow().getPort();
server.stop();
```

生产环境通常保持 `auto-startup=true`。使用随机端口时只能在 `start()` 完成后读取实际地址。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-netty-websocket \
  clean verify
```

独立消费者验证：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -f integration-tests/pom.xml -pl consumer-netty-websocket test
```

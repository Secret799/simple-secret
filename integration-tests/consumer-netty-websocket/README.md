# Netty WebSocket Starter Consumer Test

该模块验证独立 Netty WebSocket starter 可通过 BOM 无版本引入，默认不会监听端口，并能发现宿主应用提供的
认证器和消息处理器。测试设置 `auto-startup=false`，不会占用网络端口。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-netty-websocket</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-netty-websocket test
```

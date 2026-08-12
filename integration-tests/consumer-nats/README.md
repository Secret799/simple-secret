# NATS Starter Consumer Test

该模块验证 NATS starter 在没有客户端配置时仍可安全创建 `NatsClientManager`，且不会尝试连接服务器。
生产使用时按 README 配置客户端、请求超时和受控消息处理线程池。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-nats</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-nats test
```

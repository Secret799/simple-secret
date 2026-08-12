# MQTT v5 Starter Consumer Test

该模块验证第三方 Spring Boot 应用只引入 MQTT v5 starter 时能够使用自动配置、发布订阅与消息上下文 API。
测试不配置客户端，不会连接 Broker；模块直接依赖 Jackson，不依赖已移除的 JSON 模块。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-mqttv5</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-mqttv5 test
```

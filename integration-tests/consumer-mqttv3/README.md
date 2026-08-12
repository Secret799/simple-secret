# MQTT v3 Starter Consumer Test

该模块验证第三方 Spring Boot 应用只引入 MQTT v3 starter 时能够发现自动配置和公开 API。测试不配置客户端，
因此不会连接 Broker；生产接入需在 `simple-secret.mqttv3.clients` 下显式声明客户端。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-mqttv3</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-mqttv3 test
```

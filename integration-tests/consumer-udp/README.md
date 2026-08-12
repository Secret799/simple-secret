# UDP Consumer Test

该模块从第三方应用类路径验证 `simple-secret-plugin-udp` 的 JDK-only 单播、组播 API 和监听器生命周期。
测试使用受控监听器，不依赖 Spring，也不向外部网络发送业务报文。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-udp</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-udp test
```

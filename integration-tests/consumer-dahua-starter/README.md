# Dahua Starter Consumer Test

该模块验证第三方应用只声明大华 Spring Boot starter 时可以发现自动配置和传递的 SDK 公开类型。测试保持
starter 默认关闭，不加载 NetSDK 原生库，也不连接真实设备。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-dahua</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-dahua-starter test
```

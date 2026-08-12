# Camera SDK API Consumer Test

该模块模拟业务自行实现摄像机厂商能力，只依赖 `simple-secret-plugin-camera-sdk` 的领域模型、SPI 和注册表。
测试验证核心 API 不要求 Spring 或 JNA，并可按厂商名称解析业务实现。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-camera-sdk</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-camera-sdk test
```

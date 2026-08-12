# Camera Starter Consumer Test

该模块模拟只引入 Camera starter 的 Spring Boot 应用，验证自动配置可发现 RTSP 地址组装服务，并覆盖海康
NVR 子码流地址生成。测试只组装字符串，不连接摄像机或 NVR。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-camera</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-camera test
```

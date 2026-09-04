# Camera-to-ZLM Starter Consumer Test

该模块验证第三方应用只声明 Camera-to-ZLM starter 时可以发现两段自动配置。测试保持 Camera SDK、EasyMedia
和 ZLM4J 默认关闭，不加载大华 NetSDK、ZLMediaKit 或 FFmpeg 原生库。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-camera-zlm</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-camera-zlm test
```

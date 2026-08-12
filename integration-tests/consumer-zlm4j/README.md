# ZLM4J Starter Consumer Test

该模块验证 ZLM4J starter 的安全默认配置。测试保持 `simple-secret.zlm4j.enabled=false`，只加载配置属性与
媒体资源策略，不加载本地动态库，也不启动媒体服务。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-zlm4j</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-zlm4j test
```

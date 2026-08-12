# Toolbox Consumer Test

该模块模拟只依赖 `simple-secret-common-toolbox` 的普通 Java 17 应用，验证 BOM 可省略版本号，且缓存、
动态列契约与转换器不会传递 Spring、Hutool 等非必要依赖。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-common-toolbox</artifactId>
</dependency>
```

测试覆盖过期缓存替换通知和动态列扩展接口，不连接外部服务。先在项目根目录执行 `mvn install -DskipTests`，
再运行：

```bash
mvn -f integration-tests/pom.xml -pl consumer-toolbox test
```

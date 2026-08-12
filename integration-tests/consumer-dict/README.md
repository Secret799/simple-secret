# Dict Consumer Test

该模块模拟只依赖 `simple-secret-common-dict` 的 Java 17 应用，验证 toolbox 能作为必要传递依赖获得，
并覆盖枚举注册、业务数据源、TTL 缓存失效和 `@DictField` 对象翻译。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-common-dict</artifactId>
</dependency>
```

测试使用内存数据源，不需要 Spring、数据库或 Redis。执行方式：

```bash
mvn -f integration-tests/pom.xml -pl consumer-dict test
```

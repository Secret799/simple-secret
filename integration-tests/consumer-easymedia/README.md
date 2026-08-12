# EasyMedia Starter Consumer Test

该模块验证 EasyMedia 自动配置在宿主显式提供 Web MVC 与 Validation 栈时可以被发现。测试关闭 EasyMedia 和
ZLM4J，不连接 Redis、ZLMediaKit 或浏览器，也不加载本地媒体动态库。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-easymedia</artifactId>
</dependency>
```

宿主还需按使用场景提供 `spring-boot-starter-web` 和 `spring-boot-starter-validation`。执行方式：

```bash
mvn -f integration-tests/pom.xml -pl consumer-easymedia test
```

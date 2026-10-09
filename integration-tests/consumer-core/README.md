# Core Consumer Test

该模块模拟第三方项目通过 BOM 无版本引入 Common Core，并验证通用响应模型与状态码 API 可直接使用。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-common-core</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-core test
```

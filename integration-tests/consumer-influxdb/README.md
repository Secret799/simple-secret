# InfluxDB Starter Consumer Test

该模块验证 InfluxDB starter 的自动配置、注解映射和安全查询 DSL 能被独立应用解析。测试关闭真实连接，
不会访问 InfluxDB；生产环境必须通过外部配置注入地址和凭据。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-influxdb</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-influxdb test
```

# Geo Plugin Consumer Test

该模块模拟第三方项目通过 BOM 无版本引入 Geo 插件，并验证 WGS84 与 ECEF 坐标转换 API 可直接使用。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-geo</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-geo test
```

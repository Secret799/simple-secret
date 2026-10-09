# KMZ Plugin Consumer Test

该模块模拟第三方项目通过 BOM 无版本引入 KMZ 插件，并验证 KML 坐标与 KMZ 读取限制 API 可直接使用。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-kmz</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-kmz test
```

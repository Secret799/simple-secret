# Excel Consumer Test

该模块验证第三方应用只引入 `simple-secret-plugin-excel` 后即可在内存中完成工作簿导出和批量导入，
包括公开模型、处理器和 EasyExcel 传递依赖。测试启用 headless 模式，不读写用户文件。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-excel</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-excel test
```

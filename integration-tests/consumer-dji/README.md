# DJI Starter Consumer Test

该模块模拟引入 DJI starter 的 Servlet 应用，验证 SEI 解析回调与 SSE 诊断端点可以从已安装制品自动配置。
测试显式关闭 EasyMedia 和 ZLM4J，不加载本地媒体动态库，也不连接真实 DJI 设备。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-dji</artifactId>
</dependency>
```

宿主需要提供 Web MVC 与 Validation 栈。执行方式：

```bash
mvn -f integration-tests/pom.xml -pl consumer-dji test
```

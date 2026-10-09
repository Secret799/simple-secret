# GB28181 Starter Consumer Test

该独立 consumer 只依赖 `simple-secret-springboot-starter-gb28181`，从仓库外应用视角验证：

- BOM 可以无版本管理 starter；
- starter 传递公开的 GB28181 核心 API，包括报警复位、关键帧请求、报警模型、监听器、报警/目录/位置订阅与容量、录像和看守位查询；
- Spring Boot 可以发现自动配置和配置元数据；
- 未显式启用时不创建或启动 `Gb28181Server`；
- 配置只限制订阅容量，不会自动发起报警/目录/位置订阅；
- 依赖树不会带入 ZLMediaKit 或 Spring Web。

运行前先在仓库根目录安装当前制品，然后执行：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -f integration-tests/consumer-gb28181/pom.xml test
```

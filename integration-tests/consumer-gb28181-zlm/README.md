# GB28181-ZLM Consumer

仅依赖已安装的 `simple-secret-springboot-starter-gb28181-zlm`，验证公开实时点播/历史回放/下载 API、自动配置 imports、配置元数据及默认关闭行为。测试不启动 SIP 或加载 ZLM native。

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn install -DskipTests
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -f integration-tests/pom.xml -pl consumer-gb28181-zlm -am test
```

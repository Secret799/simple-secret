# GB28181 Plugin Consumer

只依赖已安装的 `simple-secret-plugin-gb28181`，验证公开配置/凭据/报警模型与监听器/报警订阅/目录订阅与通知/移动位置订阅/目录/录像/设备与看守位/PTZ 精确位置查询/PTZ/预置位查询与控制/拉框放大/缩小、报警复位、关键帧请求、远程启动、录像和报警布防/实时/历史点播与下载信令 API 和无 ZLM、厂商 SDK 依赖。
测试本身继承 consumer 父 POM 的 Spring 测试工具，不用于推断插件是否依赖 Spring。

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -f integration-tests/pom.xml -pl consumer-gb28181-plugin test
```

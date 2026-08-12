# Dahua SDK Consumer Test

该模块验证大华 SDK 的配置、预览会话和热成像公开模型可以在未安装 NetSDK 的环境中加载。真实调用仍要求
部署主机提供匹配平台与位数的厂商动态库，并正确配置动态库目录。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-camera-sdk-dahua</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-camera-sdk-dahua test
```

# Hikvision SDK Consumer Test

该模块验证第三方应用可以加载海康 SDK 公开类型并创建 `HikvisionSdkOptions`，且仅访问配置模型时不会提前
加载厂商原生库。真实登录、云台和录像检索仍要求部署主机安装匹配平台与位数的 HCNetSDK。

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-camera-sdk-hikvision</artifactId>
</dependency>
```

```bash
mvn -f integration-tests/pom.xml -pl consumer-camera-sdk-hikvision test
```

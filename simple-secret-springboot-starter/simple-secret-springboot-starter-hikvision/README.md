# 海康威视 Camera SDK Spring Boot Starter

`simple-secret-springboot-starter-hikvision` 为 HCNetSDK 驱动提供按需自动配置和 Spring 生命周期管理。
模块不会携带或下载厂商原生库，仅支持底层插件声明的 Windows 和 Linux 环境。

## Maven 依赖

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-hikvision</artifactId>
</dependency>
```

## 配置

```yaml
simple-secret:
  camera-sdk:
    hikvision:
      enabled: true
      library-directory: ${HIKVISION_SDK_HOME}
      file-search-timeout: 5s
      async-ptz-queue-capacity: 256
```

starter 默认关闭。启用后创建单例 `HikvisionCameraSdkService`，并在 Spring 容器关闭时调用
`close()`。宿主声明同类型 Bean 时自动配置会回退；也可提供自定义
`HikvisionCameraSdkServiceFactory` 控制服务创建过程。

设备账号和密码不属于 starter 配置，应在每次设备操作时通过业务侧安全配置注入。

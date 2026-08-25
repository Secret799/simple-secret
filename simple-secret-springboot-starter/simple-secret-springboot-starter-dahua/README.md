# 大华 Camera SDK Spring Boot Starter

`simple-secret-springboot-starter-dahua` 为大华 NetSDK 驱动提供按需自动配置和 Spring 生命周期管理。
模块不会携带或下载厂商原生库，部署环境需自行提供与操作系统和 CPU 架构匹配的 NetSDK；底层驱动
仅支持 Windows 和 Linux。

## Maven 依赖

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-dahua</artifactId>
</dependency>
```

## 配置

```yaml
simple-secret:
  camera-sdk:
    dahua:
      enabled: true
      library-directory: ${DAHUA_SDK_HOME}
      operation-timeout: 3s
      radiometry-search-timeout: 5s
      async-ptz-queue-capacity: 256
      max-radiometry-results: 10000
```

starter 默认关闭。启用后创建单例 `DahuaCameraSdkService`，并在 Spring 容器关闭时调用
`close()`。宿主声明同类型 Bean 时自动配置会回退；也可提供自定义
`DahuaCameraSdkServiceFactory` 控制服务创建过程。

设备账号和密码不属于 starter 配置，应在每次设备操作时通过业务侧安全配置注入。

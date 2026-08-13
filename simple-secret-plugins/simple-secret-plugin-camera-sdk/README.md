# Simple Secret Camera SDK Plugin

`simple-secret-plugin-camera-sdk` 面向 Java 17，提供摄像机厂商 SDK 的领域模型、能力 SPI 和不可变实例注册表。模块没有生产依赖，可用于普通 Java、Spring 或其他依赖注入环境。

该模块是 Honeybee `integrated-camera-sdk-basic` 的最小依赖重构，不包含厂商 JNA 声明、原生二进制、Spring 自动配置、登录定时缓存、全局异常处理器或 ZLM4J 推流。

## Maven 依赖

导入 Simple Secret BOM 后声明：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.ss</groupId>
            <artifactId>simple-secret-common-bom</artifactId>
            <version>1.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.ss</groupId>
        <artifactId>simple-secret-plugin-camera-sdk</artifactId>
    </dependency>
</dependencies>
```

未使用 BOM 时指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-camera-sdk</artifactId>
    <version>1.1.0</version>
</dependency>
```

## 领域对象

领域对象保留 Honeybee 的链式 setter，便于迁移现有调用代码，同时不再依赖 Lombok：

```java
DeviceDomain device = new DeviceDomain()
        .setDeviceId("camera-01")
        .setDeviceName("north-gate")
        .setIp("192.0.2.10")
        .setPort("8000")
        .setUsername(System.getenv("CAMERA_USERNAME"))
        .setPassword(System.getenv("CAMERA_PASSWORD"))
        .setChannel("1");

LoginDomain login = device.toLoginDomain();
```

`DeviceDomain` 和 `LoginDomain` 不会修剪、打印或校验凭据。厂商驱动应在调用原生 API 前校验主机、端口和必填字段，错误消息不得包含账号、密码或完整连接串。

PTZ 速度等级统一为 1 到 10，默认 5，可映射到厂商实际范围：

```java
PTZControlDomain control = new PTZControlDomain()
        .setCommand(PtzControlCommandEnums.RIGHT_UP)
        .setIsBegin(true)
        .setSpeedLevel(7);

int hikvisionSpeed = control.getSpeed(1, 7);
```

`PlayDomain` 包含回放区间、码流协议和编解码参数；`VideoParam` 未设置帧率时返回 25。`PlaybackTimePeriodDomain` 的时间段列表保持可变，供厂商查询实现逐条填充。

## 实现厂商能力

能力按用途拆分，驱动只实现实际支持的接口：

```java
public final class AcmePtzService implements PtzControlService {
    @Override
    public String product() {
        return "Acme";
    }

    @Override
    public boolean syncControl(DeviceDomain device, PTZControlDomain control) {
        // 调用 Acme 原生 SDK；不要记录 device 中的凭据。
        return true;
    }
}
```

未覆盖的方法默认抛出 `UnsupportedCameraSdkOperationException`，不会静默返回失败值。

## 服务注册表

调用方显式传入服务实例，不扫描 classpath，不读取静态 Spring 上下文：

```java
CameraSdkServiceRegistry registry = new CameraSdkServiceRegistry(List.of(
        acmeLoginService,
        acmePtzService,
        acmePlayService,
        acmePlayQueryService));

PtzControlService ptz = registry.requirePtz("acme");
AcmePlayService play = registry.requirePlay("Acme", AcmePlayService.class);
String streamId = play.realPlay(device, request, target);
```

播放服务通过具体实现类型取回，调用方可以安全传入驱动定义的 target 类型。产品编码查找不区分大小写；空白编码抛出 `IllegalArgumentException`，合法但未注册的能力抛出 `UnsupportedCameraSdkOperationException`。同一产品、同一能力出现两个实现时，构造注册表立即失败；登录、PTZ、播放和回放查询互相独立，不会互相覆盖。

## 与 Camera URL Starter 的关系

- `simple-secret-springboot-starter-camera` 只根据参数组装 RTSP URL，不调用厂商 SDK。
- `simple-secret-plugin-camera-sdk` 只定义厂商 SDK 领域对象和 SPI，不依赖 Spring。
- 海康/大华 JNA 驱动作为独立 opt-in 构件提供；海康驱动已支持原始实时预览和按时间回放数据回调。
- 大华 H.264 数据转推已由独立的 `simple-secret-springboot-starter-camera-zlm` 提供，不会强制传递给只使用登录或 PTZ 的应用。

`PlayService` 的 target 和返回类型由厂商驱动定义。海康实现接收 `HikvisionStreamDataHandler` 并返回可关闭的 `HikvisionStreamSession`；回调数据是已经脱离 native 内存生命周期的 HCNetSDK 原始数据，而不是通用解码帧。调用方不得假设不同厂商驱动返回相同封装格式。

海康原始回调可能包含系统头或 PS 流，不能直接输入 H.264 Annex-B publisher；需要成熟 PS 解复用或
HCNetSDK/PlayCtrl ES 回调的额外 opt-in 能力后才能转推。Camera SDK API 和海康驱动不会因此依赖 ZLM。

## 原生库安全边界

本模块不包含也不下载任何 `.dll`、`.so` 或 `.dylib`。后续厂商驱动必须由部署环境显式提供与操作系统、CPU 架构和厂商 SDK 版本匹配的原生库，并仅在相应驱动启用时加载。原生库路径、加载错误和崩溃日志不得暴露部署目录中的密钥或设备凭据。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-plugins/simple-secret-plugin-camera-sdk \
  -am clean verify
```

测试覆盖链式领域对象、凭据不变转换、PTZ Bean 属性和速度映射、播放默认值、厂商错误元数据、强类型服务选择、重复能力和默认不支持行为。

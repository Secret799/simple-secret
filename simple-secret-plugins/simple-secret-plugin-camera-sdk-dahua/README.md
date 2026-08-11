# Simple Secret Dahua Camera SDK Plugin

`simple-secret-plugin-camera-sdk-dahua` 是大华 NetSDK 的按需 JNA 驱动，提供显式登录/注销、同步或有界异步 PTZ、原始 Annex-B H.264 预览、热成像订阅和抓取、点/规则/区域测温以及有界历史测温查询。

生产运行时只依赖 `simple-secret-plugin-camera-sdk` 与 JNA；不依赖 Spring、JSON、ZLM、Hutool、Lombok 或 Flink CDC，也不包含、下载或解压厂商 `.dll`、`.so`、`.dylib`。

## Maven 依赖

导入 Simple Secret BOM 后声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-camera-sdk-dahua</artifactId>
</dependency>
```

未使用 BOM 时增加 `<version>1.1.0</version>`。JNA 由本驱动直接声明，应用不需要重复声明。

## 准备原生 SDK

部署环境必须自行取得与操作系统、CPU 架构和设备版本匹配的大华 NetSDK。驱动只支持 Windows 与 Linux，调用 `DahuaCameraSdkService.open` 时会明确拒绝 macOS 和其他平台。

`DahuaSdkOptions` 接收的是厂商库所在目录，不是单个库文件路径。Windows 目录至少包含：

```text
dhnetsdk.dll
```

Linux 目录至少包含：

```text
libdhnetsdk.so
```

驱动只通过 JNA 加载 `dhnetsdk.dll` 或 `libdhnetsdk.so`；不会加载或绑定 `dhconfigsdk`、`ImageAlg` 等未使用的组件。若厂商主库还有传递依赖，Windows 应按厂商要求放在主库同目录或加入进程 `PATH`，Linux 应在启动 JVM 前配置 `LD_LIBRARY_PATH` 或使用厂商提供的 rpath 布局。仅设置 `DahuaSdkOptions.libraryDirectory` 不能替代系统动态链接器对传递依赖的查找。模块不会把原生库路径写入异常，不会随构件发布 native 二进制，也不会默认开启厂商文件日志。

## 打开与关闭

构造 `DahuaSdkOptions` 不加载库；只有 `open` 才校验 Windows/Linux 所需文件、加载 JNA 接口并初始化 NetSDK。必须为每次成功打开的服务执行 `close`：

```java
Path sdkDirectory = Path.of(System.getenv("DAHUA_SDK_HOME"));

try (DahuaCameraSdkService dahua =
        DahuaCameraSdkService.open(DahuaSdkOptions.defaults(sdkDirectory))) {
    // 登录、PTZ、预览或热成像操作
}
```

`close()` 幂等，会先停止异步 PTZ，再关闭仍存活的热成像订阅与预览、注销会话，最后清理 NetSDK。NetSDK 是进程级运行时，同一进程同时只允许打开一个服务实例；关闭后才能重新打开。可显式设置单次操作超时、历史查询超时、异步 PTZ 队列容量和查询结果上限：

```java
DahuaSdkOptions options = new DahuaSdkOptions(
        sdkDirectory,
        Duration.ofSeconds(3),
        Duration.ofSeconds(8),
        128,
        5_000);
```

单次 native 超时不能超过 `Integer.MAX_VALUE` 毫秒，异步 PTZ 队列容量上限为 `10_000`，历史查询结果上限为 `100_000`；超限配置会在构造时拒绝，不会推迟到 native 调用或大数组分配阶段。

## 登录和注销

显式登录返回的句柄由服务跟踪；不再使用时调用 `logout`。服务不缓存账号或密码，异常只包含操作类型与大华错误码。

```java
DeviceDomain device = new DeviceDomain()
        .setIp("192.0.2.10")
        .setPort("37777")
        .setUsername(System.getenv("CAMERA_USERNAME"))
        .setPassword(System.getenv("CAMERA_PASSWORD"))
        .setChannel("1");

LoggedDomain logged = dahua.login(device.toLoginDomain());
try {
    // 使用 logged.getUserId()、logged.getChannelNo() 等登录结果
} finally {
    dahua.logout(logged.getUserId());
}
```

PTZ、预览、热成像查询等设备操作会为该操作建立和释放临时登录；不要同时对同一 `LoggedDomain` 重复调用 `logout`。

## PTZ 控制

同步控制可发出单次开始/停止命令：

```java
PTZControlDomain begin = new PTZControlDomain()
        .setCommand(PtzControlCommandEnums.LEFT)
        .setSpeedLevel(7)
        .setIsBegin(true);

boolean accepted = dahua.syncControl(device, begin);
```

不设置 `isBegin` 而设置正的持续时间时，服务会在 `finally` 中发送停止命令：

```java
PTZControlDomain turn = new PTZControlDomain()
        .setCommand(PtzControlCommandEnums.RIGHT)
        .setSpeedLevel(5)
        .setDuration(Duration.ofMillis(500));

dahua.syncControl(device, turn);
```

`asyncControl` 使用单线程有界队列保持命令顺序；队列已满或服务关闭时返回 `false`。在返回 `true` 前它已完成登录与参数解析，队列内不保留账号、密码或 `DeviceDomain`。

```java
boolean queued = dahua.asyncControl(device, turn);
```

逻辑通道 `1` 映射为设备登录结果的起始通道；速度等级 1 到 10 映射并限制到大华范围 1 到 8。

## H.264 实时预览

`realPlay` 仅接受主码流 `0` 或子码流 `1`，回调只会收到以三或四字节 start code 开头的 Annex-B H.264 数据。回调的 `DahuaStreamFrame` 会复制字节数组；调用方必须关闭返回的预览句柄。

```java
PlayDomain previewRequest = new PlayDomain().setTakeStreamParam(
        new PlayDomain.TakeStreamParam().setStreamType(0));

try (DahuaRealPlaySession preview = dahua.realPlay(device, previewRequest, frame -> {
    byte[] annexB = frame.data();
    // 交给具备 H.264 Annex-B 输入能力的解码器或传输层
})) {
    // 保持预览会话；close 时停止预览并注销其临时登录
}
```

回调异常不会跨越 native 回调边界。单个 native 帧最大接受 `16 MiB`；同一注册上的消费者回调串行执行，消费者尚未返回时到达的重叠帧会被丢弃。停止预览会先禁止新回调，并等待在途消费者在操作超时内完成；超时后本次关闭失败且不会调用 native stop，可在消费者返回后重试。模块只交付原始 H.264 帧，不提供 ZLM 推流或历史回放适配。

## 热成像订阅和抓取

订阅回调接收已与 native 内存分离的 `DahuaThermalData`；灰度和温度数组均为防御性副本。热图单维上限为 `8192`，总像素上限为 `16,777,216`，native 压缩缓冲上限为 `16 MiB`；超限或无效帧会在分配数组前丢弃。同一订阅的消费者回调串行执行，解绑会等待在途回调完成。`fetch` 触发设备热图获取并返回厂商状态（`0` 未知、`1` 空闲、`2` 正在获取）。订阅停止前必须关闭句柄：

```java
try (DahuaThermalSubscription thermal = dahua.subscribeThermal(device, data -> {
    float[] temperatures = data.temperatures();
    // 消费 width * height 个温度值
})) {
    int status = thermal.fetch();
}
```

## 温度查询和历史搜索

点坐标使用 0 到 8192 的大华坐标系；区域由 3 到 8 个点组成。每个同步查询均使用临时登录，并在结束时释放：

```java
DahuaTemperatureSummary point = dahua.queryPointTemperature(device, 4096, 4096);
DahuaTemperatureSummary item = dahua.queryItemTemperature(device, 1, 2, 0);
DahuaRegionTemperature region = dahua.queryRegionTemperature(device, List.of(
        new DahuaPoint(100, 100),
        new DahuaPoint(300, 100),
        new DahuaPoint(200, 300)));
```

`queryItemTemperature` 的 `meterType` 为 0 到 3，预置点与规则 ID 不可为负。历史查询的 `period` 只允许 `0`、`5`、`10`、`15` 或 `30`，并受到 `DahuaSdkOptions` 的总超时和最大结果数限制；不论成功、超时还是异常，native 查找句柄都会关闭：

```java
List<DahuaRadiometryRecord> records = dahua.searchRadiometry(
        device,
        0,
        5,
        LocalDateTime.of(2026, 8, 1, 0, 0),
        LocalDateTime.of(2026, 8, 2, 0, 0));
```

## 安全边界

- 不要把 `DeviceDomain`、原始码流、完整 RTSP 地址、账号或密码写入日志和异常。
- 原生 SDK 崩溃属于进程级故障，高风险设备建议由独立服务进程隔离。
- 只允许受控部署配置选择原生库目录，不要接受不可信输入。
- 本模块不迁移 Flink CDC，不提供 ZLM 推流，也不提供厂商 native 二进制。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-plugins/simple-secret-plugin-camera-sdk-dahua \
  -am verify
```

单元测试使用窄原生接口替身，不要求安装真实大华 NetSDK，也不会触发 `Native.load`。

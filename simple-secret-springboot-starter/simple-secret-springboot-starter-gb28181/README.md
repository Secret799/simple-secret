# Simple Secret GB28181 Starter

`simple-secret-springboot-starter-gb28181` 为 Java 17 和 Spring Boot 3.5 应用自动配置平台侧 GB28181 SIP 服务。处理设备/NVR 的 Digest 鉴权注册、心跳、报警、目录与移动位置订阅、目录/录像/设备信息/状态/看守位/PTZ 精确位置查询、PTZ/预置位、报警复位、关键帧请求、实时点播、历史回放与录像下载 INVITE/ACK/INFO/BYE 信令，不提供级联或媒体流服务。

starter 只依赖纯 Java `simple-secret-plugin-gb28181` 和 Spring Boot 自动配置，不依赖 Spring Web、数据库、Redis、ZLMediaKit 或摄像机厂商 SDK。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-gb28181</artifactId>
</dependency>
```

## 设备密码

启用 starter 前，宿主必须提供线程安全且能快速返回的 `DeviceCredentials` Bean。密码应从外部密钥系统或应用自己的安全存储读取，不能写进源码、日志或仓库配置。未知、停用或不允许接入的设备返回 `Optional.empty()`：

```java
import com.ss.gb28181.DeviceCredentials;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GbCredentialsConfiguration {
    @Bean
    DeviceCredentials deviceCredentials(DevicePasswordRepository passwords) {
        return passwords::findPassword;
    }
}
```

如果设置 `enabled: true` 却没有该 Bean，应用会启动失败，不会静默关闭鉴权或跳过服务创建。

## 配置

starter 默认关闭。启用时 `server-id` 和 `realm` 必填；示例编码、地址和域名均为占位值：

```yaml
simple-secret:
  gb28181:
    enabled: true
    server-id: "34020000002000000001"
    realm: example.test
    bind-address: 127.0.0.1
    advertised-address: 127.0.0.1
    port: 5060
    registration-ttl: 1d
    heartbeat-interval: 60s
    heartbeat-misses: 3
    query-timeout: 10s
    invite-timeout: 10s
    stop-timeout: 5s
    max-play-sessions: 256
    max-devices: 1000
    max-pending-queries: 256
    max-pending-alarms: 256
    max-alarm-subscriptions: 256
    max-catalog-subscriptions: 256
    max-pending-catalog-notifications: 256
    max-mobile-position-subscriptions: 256
    max-pending-mobile-position-notifications: 256
    max-mobile-position-items: 1000
    max-catalog-items: 10000
    max-record-items: 10000
    max-message-bytes: 65536
```

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `false` | 是否启动 UDP 和 TCP SIP 监听 |
| `server-id` | 无 | 平台的 20 位 SIP 国标编码，启用默认服务时必填 |
| `realm` | 无 | Digest 鉴权 realm，启用默认服务时必填 |
| `bind-address` | `127.0.0.1` | UDP/TCP 本地监听的数字 IP 地址 |
| `advertised-address` | `127.0.0.1` | SIP 报文向设备公布的可达数字 IP 地址 |
| `port` | `5060` | UDP 和 TCP 共用的 SIP 端口 |
| `registration-ttl` | `1d` | 最大注册有效期，使用整秒值 |
| `heartbeat-interval` | `60s` | 设备约定心跳间隔 |
| `heartbeat-misses` | `3` | 连续缺失多少次心跳后判定离线 |
| `query-timeout` | `10s` | MESSAGE、历史回放 INFO 及报警/目录/位置 SUBSCRIBE 建立/续订的完成期限 |
| `invite-timeout` | `10s` | 点播协商期限，100ms～2min |
| `stop-timeout` | `5s` | BYE 及 `Expires: 0` 报警/目录/位置退订的应答期限，100ms～30s |
| `max-play-sessions` | `256` | 实时/历史/下载会话及32秒清理窗口总上限，1～9999 |
| `max-devices` | `1000` | 最大在线设备数 |
| `max-pending-queries` | `256` | 目录、录像、设备信息/状态查询、PTZ/预置位/报警复位/关键帧控制接收确认及报警独立响应共享的最大挂起 MESSAGE 数 |
| `max-pending-alarms` | `256` | 已接纳且尚未完成的报警回调上限，包含正在执行的回调，1～10000 |
| `max-alarm-subscriptions` | `256` | 报警订阅上限，包含已停止或中止后的清理项，1～10000 |
| `max-catalog-subscriptions` | `256` | 目录订阅及清理项总上限，1～10000 |
| `max-pending-catalog-notifications` | `256` | 目录通知排队和执行回调总上限，1～10000 |
| `max-mobile-position-subscriptions` | `256` | 位置订阅及清理项总上限，1～10000 |
| `max-pending-mobile-position-notifications` | `256` | 位置通知排队和执行回调总上限，1～10000 |
| `max-mobile-position-items` | `1000` | 单条位置通知声明的条目数上限，1～10000 |
| `max-catalog-items` | `10000` | 目录查询及单条通知声明的条目数上限 |
| `max-record-items` | `10000` | 单次录像查询最大聚合条目数，1～100000 |
| `max-message-bytes` | `65536` | 单条 SIP 消息最大字节数 |

`bind-address` 是本机监听地址；`advertised-address` 是设备应能访问的平台地址。生产部署在 NAT 或多网卡环境时，两者可能不同。地址只接受数字 IPv4/IPv6，端口范围、时间和容量边界由核心配置统一校验，无效配置会使应用启动失败。

## 使用与生命周期

注入 `Gb28181Server` 后可读取当前在线注册快照并发起目录查询：

```java
import com.ss.gb28181.CatalogItem;
import com.ss.gb28181.Gb28181Server;

import java.util.List;
import java.util.concurrent.CompletableFuture;

List<String> onlineDeviceIds = server.devices().stream()
        .map(device -> device.deviceId())
        .toList();

CompletableFuture<List<CatalogItem>> catalog = server.queryCatalog(deviceId);
```

目录响应可能拆成多条 SIP MESSAGE；返回的 future 只会在完整聚合、超时或失败后完成。业务处理应使用异步 continuation，避免在 SIP 回调或维护线程上执行阻塞逻辑。

默认 Bean 以 `destroyMethod = "close"` 注册。Spring 容器关闭时会停止 UDP/TCP 监听、定时任务和挂起查询；应用不应再次接管该默认 Bean 的资源所有权。宿主提供自己的 `Gb28181Server` Bean 时，starter 会退让，也不会强制创建 `DeviceCredentials` 或校验 starter 的服务参数；此时宿主负责为自定义 Bean 声明正确的销毁方式。

## 报警回调

宿主可提供一个可选的 `GbAlarmListener` Bean 接收已认证设备上报的报警。没有监听器时服务仍会启动，但拒绝报警接纳；存在多个未指定唯一候选的监听器时，Spring 会按常规 Bean 歧义规则使启动失败。

```java
@Bean
GbAlarmListener gbAlarmListener() {
    return event -> alarmService.accept(event.sourceDeviceId(), event.alarm());
}
```

回调按接纳顺序在组件自有的单个守护线程执行，不占用 SIP 或维护线程。`sourceDeviceId` 是已认证注册设备，报警 XML 中的 `alarm().deviceId()` 可以是其通道或 10 位报警中心编码；核心不校验通道归属，宿主必须在业务使用前授权。回调异常不会阻断后续报警。关闭服务会丢弃排队任务并中断正在执行的回调，因此监听器应响应线程中断。报警仅在内存中处理，可能因新的 SIP 事务重复送达；持久化和幂等由宿主负责。

报警订阅由宿主在设备完成注册后显式调用 `Gb28181Server.subscribeAlarms(...)`。starter 不根据配置自动订阅，
也不创建额外订阅 Bean；订阅通知继续使用同一个可选 `GbAlarmListener` 和 `max-pending-alarms` 回调容量。
订阅数量独立受 `max-alarm-subscriptions` 限制，返回的 `GbAlarmSubscription` 负责自动续订，并通过
`stop()` 或 `close()` 发起取消。完整过滤条件、完成语义和设备离线清理规则见核心模块文档。

## 目录订阅

可选提供一个 `GbCatalogListener` Bean，订阅在设备注册后由宿主显式调用：

```java
@Bean
GbCatalogListener gbCatalogListener() {
    return event -> catalogService.accept(event.sourceDeviceId(), event.notification());
}

// 设备注册后：
var opening = server.subscribeCatalog(deviceId,
        GbCatalogSubscriptionRequest.all(Duration.ofMinutes(10)));
```

starter 不自动订阅或建立目录缓存；没有目录监听器时不能发起目录订阅。目录与报警监听器独立可选，
各自使用独立的有界队列和执行线程。多个未指定唯一候选的同类型监听器按 Spring 常规歧义规则使启动失败。
`max-catalog-subscriptions` 包含清理窗口，`max-pending-catalog-notifications` 包含排队及执行中的通知。
采用 `Event: Catalog;id=数字`；返回句柄自动续订，`stop()` 退订。无 Event 的条目不是完整快照，宿主需自行
协调全量目录和增量事件。关闭容器会停止订阅及回调线程，监听器应响应中断。完整事件类型、时间、容量和
清理语义见 [核心目录订阅文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md#目录订阅与变更通知)。

## 移动设备位置订阅

可选提供一个独立的 `GbMobilePositionListener` Bean，在设备注册后显式订阅：

```java
@Bean
GbMobilePositionListener gbMobilePositionListener() {
    return event -> positionService.accept(event.sourceDeviceId(), event.notification());
}

// 设备注册后：
var opening = server.subscribeMobilePosition(deviceId,
        new GbMobilePositionSubscriptionRequest(Duration.ofMinutes(10), Duration.ofSeconds(5)));
```

starter 不自动订阅或保存轨迹。没有位置监听器时不能发起位置订阅；多个未指定唯一候选的同类型监听器按
Spring 常规歧义规则使启动失败。位置、报警和目录监听器独立可选，使用各自有界回调线程。
租期与间隔为 1～86400 整秒，一参数请求构造器默认间隔 5 秒。接收 2022 版批量位置通知，保留通知和采集时间、
WGS84 经纬度及可选速度/方向/海拔/距地高度；宿主核对条目归属并负责持久化、幂等和坐标转换。
三项位置容量分别限制订阅及清理项、排队与执行通知、单条通知声明条目数。
返回句柄自动续订，`stop()` 退订；容器关闭清理订阅和回调线程，监听器应响应中断。
完整结构、单位、`Event: presence` 直连配置及清理语义见
[核心位置订阅文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md#移动设备位置订阅)。

## 设备查询与云台

同一个 `Gb28181Server` Bean 提供 `queryDeviceInfo(deviceId)`、`queryDeviceStatus(deviceId)` 和
`ptz(deviceId, channelId, PtzCommand)`。查询返回强类型 future；PTZ 使用 `PtzCommand.move(...)`、
`zoom(...)` 或 `stop()`。调用方负责通道授权，并在动作结束时显式发送停止。
控制 future 只表示 SIP 接收确认；其取消/超时不撤回已发送的机械动作。
完整字段、速度范围、应答及容量语义见 [核心文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md)。

同一 Bean 可调用 `server.queryPresets(deviceId, channelId)` 获取不可变预置位列表，通过
`server.preset(deviceId, channelId, PresetCommand.set(1)/goTo(1)/remove(1))` 显式设置、调用或删除预置位。
控制编号为 1～255；查询返回的 `PresetItem` 保留字符串编号与名称，不能假定厂商编号都能转成控制参数。
查询等待完整 XML 列表，控制只等待 SIP 接收确认；取消不撤回设备操作。两类请求共用
`max-pending-queries` 和 `query-timeout`，无需新增配置；详情见
[预置位文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md#云台预置位)。

## 报警复位与关键帧请求

通过同一个 `Gb28181Server` Bean 显式调用：

```java
// 宿主确认报警处置与通道授权后，选择需要复位的方式和类型。
var command = new GbAlarmResetCommand(Set.of(2), 1);
var received = server.resetAlarm(deviceId, channelId, command);
```

`GbAlarmResetCommand.all()` 表示全部方式；可选类型仅支持单一方式 2/5/6，分别取 1～5、1～13、1～2。
需要 IDR 帧时调用 `server.requestKeyFrame(deviceId, channelId)`；它不会建立新点播，也不确认已收到关键帧。
两个入口均只等待 SIP 接收确认，共用 `max-pending-queries` 和 `query-timeout`。取消不撤回已经发送的设备操作。
starter 默认关闭，不自动发送复位或关键帧请求，也不新增配置、Bean 或依赖；宿主明确选择目标与操作时机。
完整编码、筛选条件、目标授权、设备状态影响及清理语义见
[核心控制文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md#报警复位与关键帧请求)。

## 录像目录检索

调用 `server.queryRecordInfo(deviceId, channelId, RecordQuery.all(startTime, endTime))` 获取录像列表。
起止时间为设备本地 `LocalDateTime`，精确到整秒，开始严格早于结束；不隐式转换时区。
使用 `new RecordQuery(startTime, endTime, RecordQuery.Type.ALARM)` 可按报警录像检索。
返回 future 等待分页聚合完成，受 `max-record-items` 和共享 MESSAGE 容量/超时限制。
宿主负责通道授权，结果中的路径只作为设备元数据；字段、去重和边界语义见
[核心文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md#录像目录检索)。

## 实时点播

注入同一个 `Gb28181Server`，使用 `server.play(deviceId, channelId, new GbRtpTarget(mediaIp, mediaPort, transport))`
建立实时点播；宿主先开启媒体接收端口，协商失败/取消及会话结束时释放端口。`GbPlaySession.stop()` 异步停止，
`completion()` 观察设备挂断、离线及关闭。协议范围、32 秒清理窗口和源码示例见
[核心文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md)。

需要内嵌 ZLM 自动分配/释放接收端口时，单独引入
[GB28181-ZLM starter](../simple-secret-springboot-starter-gb28181-zlm/README.md)。信令 future 成功不等于视频已可播放。

## 历史回放

同一 Bean 提供 `server.playback(deviceId, channelId, target, range)`。`range` 是
`GbPlaybackRange(Instant startTime, Instant endTime)`，宿主明确转换设备时间，起止必须精确到整秒。
返回 `GbPlaySession` 后通过 `control(GbPlaybackControl.pause()/resume()/seek(...)/speed(...))`
发送回放控制；每会话仅允许一个待确认 INFO，沿用 `query-timeout`。实时与历史共享 `max-play-sessions`。
标准结束通知经身份校验后触发 BYE 和资源清理。完整时间、控制和结束语义见
[核心文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md#历史回放与控制)。

## 录像下载信令

使用 `server.download(deviceId, channelId, target, new GbDownloadRequest(range, speed))` 发起 RTP 下载。
范围使用 `GbPlaybackRange`，速度为 1～128 的整数；`GbDownloadRequest.normal(range)` 请求 1 倍速。
通过返回会话的 `downloadFileSize()` 读取可选设备声明字节数。下载共享点播容量和资源生命周期，
下载会话停止使用 `stop()`，不能使用回放 INFO 控制。文件保存、媒体保活、进度和完整性由接收端负责；
[核心文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md#录像下载信令) 说明完整语义。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-gb28181 -am test
```

自动配置测试使用真实回环 UDP/TCP 监听验证默认关闭、启用、缺少凭据、非法配置、宿主覆盖和容器关闭后的端口释放，不依赖真实设备或外部服务。真实厂商设备、NAT 网络兼容性和长期压力需要在部署环境继续验证。

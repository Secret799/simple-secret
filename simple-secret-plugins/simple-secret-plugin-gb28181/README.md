# Simple Secret GB28181 Plugin

面向 Java 17 的平台侧 GB/T 28181 信令组件。提供设备 REGISTER 注册/续期/注销、Digest 鉴权、
Keepalive 心跳、目录/设备信息/状态/PTZ 精确位置查询、PTZ 与预置位控制、报警复位、关键帧请求、报警上报与订阅、目录订阅、移动位置订阅、实时点播和历史回放 INVITE/ACK/INFO/BYE。支持 UDP、TCP，以及设备已有 TCP 连接上的目录下发。
不依赖 Spring、ZLM、厂商 SDK、数据库或 Redis。支持录像目录检索及下载信令；报警历史列表不在 GB/T 28181-2022 标准响应中，
报警转发、语音或上下级平台级联，不代表已完成 GB/T 28181-2022 全部符合性认证。

## 依赖

导入 Simple Secret BOM 后声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-gb28181</artifactId>
</dependency>
```

SIP 事务及报文解析使用 `javax.sip:jain-sip-ri:1.3.0-91` 和 `jain-sip-api:1.2.1.4`。
旧 Log4j API 通过 `log4j-over-slf4j` 桥接；不携带 Log4j 1.x 实现、不强制日志后端。
升级 SIP 栈时必须验证内部传输适配和网络回归测试，不能仅替换版本号。

## 使用

```java
import com.ss.gb28181.Gb28181Server;
import com.ss.gb28181.GbServerOptions;
import java.util.Optional;

var options = GbServerOptions.builder()
        .serverId(System.getenv("GB_SERVER_ID"))
        .realm(System.getenv("GB_SIP_DOMAIN"))
        .bindAddress(System.getenv("GB_BIND_IP"))
        .advertisedAddress(System.getenv("GB_ADVERTISED_IP"))
        .port(5060)
        .build();

try (var server = Gb28181Server.open(options, deviceId ->
        deviceId.equals(System.getenv("GB_DEVICE_ID"))
                ? Optional.ofNullable(System.getenv("GB_DEVICE_PASSWORD"))
                : Optional.empty())) {
    // 设备完成注册后调用；未知/离线设备会被拒绝。
    var catalog = server.queryCatalog(System.getenv("GB_DEVICE_ID"));
    catalog.thenAcceptAsync(items -> {
        // 宿主保存目录快照、展示通道；请使用宿主受管且有界的执行器。
    }, businessExecutor);
    // 宿主继续运行直至关闭。离开 try 块会停止服务，并使未完成查询异常结束。
}
```

`businessExecutor` 为宿主提供的执行器。构造 options 不联网，只有 `open` 监听配置端口的 UDP 和 TCP。
设备端设置平台编码、SIP 域、平台可达地址、端口及本机设备编码和密码。`advertisedAddress` 必须是设备
可达的明确 IP，不能填 `0.0.0.0`；`bindAddress` 可填通配监听地址。

`DeviceCredentials` 须线程安全且快速返回。生产建议从宿主已加载的凭据缓存查询；未知、禁用设备返回
`Optional.empty()`。不得在回调里进行数据库/网络等待，不记录密码或 Authorization 报文。
SIP 默认关闭报文追踪，应用也不要记录完整原始信令正文。

## 请求与设备生命周期

- REGISTER 支持 MD5 Digest 的 `qop=auth` 和不带 qop 的旧设备；验证身份、realm、URI、算法、nonce 和重放。
- nonce 绑定实际信令来源、有效期 60 秒；没有有效 challenge 时重新返回 401。nonce 容量为
  `max(256, maxDevices × 2)`，容量耗尽返回 503。
- 续注册、注销都需要认证；相同注册 Call-ID 的旧 CSeq 不得覆盖新绑定。仅存储当前有效注册，重启后设备需重新注册。
- Keepalive 要求 SIP From、XML DeviceID 与注册设备一致，实际 UDP/TCP 来源与注册绑定一致。
  通过未加密网络传输的 UDP 源地址校验无法代替网络边界防护。
- `devices()` 返回不可变在线快照。注册过期、连续心跳超时、注销会移除设备，并结束其未完成查询。
- `queryCatalog` 返回 `CompletableFuture<List<CatalogItem>>`。平台 MESSAGE 的 200 OK 仅代表请求收到；
  结果来自设备新的 MESSAGE，按设备/来源/SN 关联，直到唯一条目数量达到 SumNum 才成功。
- 支持乱序、重复页和空目录；重复条目内容冲突、SumNum 改变或超量响应会失败。不支持在一次查询中混用不同目录快照。
- `CatalogItem` 只提取 deviceId、name、parentId、status；目录项允许行政区域等 2～20 位数字编码。
  此结果只在 future 中交给宿主，不额外维护无期限目录缓存。
- `X-GB-Ver` 标识本平台 3.0，并记录设备版本。基础消息兼容旧版；完整版本能力协商与其他指令尚未实现。

## 设备信息、状态与云台控制

```java
var infoFuture = server.queryDeviceInfo(deviceId);
var statusFuture = server.queryDeviceStatus(deviceId);
var homePositionFuture = server.queryHomePosition(deviceId);
var ptzPositionFuture = server.queryPtzPosition(deviceId, channelId);
```

两类查询等待独立的 XML Response，按注册设备、实际来源、命令与 SN 关联。SIP 200 仅表示请求收到；
`Result=ERROR` 使查询异常结束。`DeviceInfo` 包含 deviceId、deviceName、manufacturer、model、firmware、
channelCount；可选字段未返回时为 null。`DeviceStatus` 包含 online（ONLINE/OFFLINE）、status（OK/ERROR），
以及可选 encode/record（ON/OFF）、deviceTime。设备时间保留 XML dateTime 原文及其可选时区。
查询得到的设备自报状态不会替代注册与心跳记录；`devices()` 继续按注册和心跳判断在线。

`queryPtzPosition` 对应标准 `PTZPosition` 查询，返回 `PtzPosition` 的 pan、tilt、zoom；设备可省略单个坐标，实际取值范围由设备能力决定。

云台按下/运动事件可以调用：

```java
import com.ss.gb28181.PtzCommand;

var received = server.ptz(deviceId, channelId,
        PtzCommand.move(PtzCommand.Pan.RIGHT, PtzCommand.Tilt.NONE, 32, 0));
```

按键释放或业务需要停止时，显式调用 `server.ptz(deviceId, channelId, PtzCommand.stop())`。
变倍使用 `PtzCommand.zoom(PtzCommand.Zoom.IN, 8)`；record 构造器支持方向与变倍组合。
水平/俯仰速度为 0～255，变倍为 0～15，0 是有效最低速度，停止由方向 NONE 决定；NONE 轴速度必须为 0。
编码输出标准 8 字节 PTZCmd，包含校验和。

`ptz` 的 future 在 SIP 2xx 时完成，表示控制消息被接收，不确认机械动作已经完成。非 2xx、超时、离线等使
future 异常结束。取消或给 future 设置超时只停止等待；已发送动作不会撤回，也不会自动补发停止指令。
本组件不启动定时运动，宿主负责通道授权和动作停止；停止指令同样受 MESSAGE 容量与设备可达性约束。
可选的 DeviceControl XML 应答只返回接收确认，不替代 SIP 结果；不提供巡航或位置反馈；预置位使用下面的独立接口。

目录、录像、预置位、设备信息、状态、看守位查询、PTZ/预置位/报警复位/关键帧控制接收确认和报警应答共同使用 `maxPendingQueries`、`queryTimeout`。
达到上限时同步拒绝新请求；取消、调用方 future 完成、超时、注销/绑定改变及关闭均释放挂起请求。
DeviceInfo 文本字段最多 256 字符，Channel 为 0～2147483647 的可选整数，DeviceTime 最多64字符；
结构校验与其他 XML 消息使用相同限制。

## 云台预置位

查询已注册 IPC/NVR 下宿主授权通道的预置位列表：

```java
var presets = server.queryPresets(deviceId, channelId);
presets.thenAcceptAsync(items -> {
    // PresetItem 提供 presetId() 与 presetName()；宿主展示设备返回的预置位。
}, businessExecutor);
```

请求使用 `Query/PresetQuery` MESSAGE。SIP 200 只表示收到请求，结果等待设备独立发送的
`Response/PresetQuery`，按来源设备、实际 UDP/TCP 端点、通道和 SN 关联。
支持分批、乱序、完全重复页及空列表；按 PresetID 去重并保留首次出现的顺序，返回不可变列表。
相同编号名称冲突、SumNum 改变或唯一条目数超量会使查询失败；不完整列表在 `queryTimeout` 到期后失败。

当前按直接通道的预置位配置限制每次最多 255 项；正文、XML 结构和累计字节数继续受现有限制。
PresetID 按标准 XML 的字符串类型保留，非空且最多 64 字符；PresetName 必须存在，允许空字符串，
最多 256 字符。查询结果不等于设备一定支持对应控制，组件不会自动将字符串编号转成数字。
格式非法的响应返回 SIP 400，查询继续等待合法结果；迟到的合法响应只确认收到，不重新创建查询。

显式控制使用独立的 `PresetCommand`：

```java
import com.ss.gb28181.PresetCommand;

// 宿主应按业务授权与用户动作选择其中一条；不要自动顺序执行三条。
var savePosition = PresetCommand.set(1);    // 保存当前位置到 1 号预置位。
var goToPosition = PresetCommand.goTo(1);  // 调用 1 号预置位。
var deletePosition = PresetCommand.remove(1); // 删除 1 号预置位。

var received = server.preset(deviceId, channelId, goToPosition);
```

构造命令不发送信令；只有调用 `server.preset(...)` 才会控制设备。编号范围为 1～255，0 保留。
设置可能覆盖已有位置，删除会移除设备上的预置位，调用可能驱动物理云台；宿主负责操作权限与业务选择。
编码使用标准 A.3.4 的 81H/82H/83H 指令、8 字节校验和，通过现有 DeviceControl/PTZCmd MESSAGE 发送。
该 API 不修改 `PtzCommand` 的移动、变倍与停止能力，也不提供预置位重命名、巡航或到位反馈。

控制 future 在有效 SIP 2xx 后成功，表示接收确认，不确认预置位已经保存、删除或云台已到位。
取消或给 future 设置超时只结束等待，不撤回已经发送的设备操作，也不自动重试或发送反向命令。
查询与控制共用 `maxPendingQueries` 和 `queryTimeout`；取消、超时、设备离线/绑定变化及服务关闭清理挂起事务。

## 报警复位与关键帧请求

设备报警复位由宿主按实际处置结果显式调用：

```java
import com.ss.gb28181.GbAlarmResetCommand;
import java.util.Set;

// 报警方式 2（设备报警）、类型 1（视频丢失）；宿主先授权目标通道。
var command = new GbAlarmResetCommand(Set.of(2), 1);
var received = server.resetAlarm(deviceId, channelId, command);
```

`GbAlarmResetCommand.all()` 表示复位目标的全部报警方式；构造命令本身不发送信令。
`methods` 取 1～7，复制为不可变集合，空集合表示全部，报文输出 `AlarmMethod=0`；
非空集合按升序以 `/` 组合。`type` 可省略；提供时要求恰好选择一种方式，当前支持：

| 报警方式 | 类型范围 |
| --- | --- |
| 2，设备报警 | 1～5 |
| 5，视频报警 | 1～13 |
| 6，设备故障报警 | 1～2 |

方式 2 未指定类型时保留设备默认复位语义。为避免组合方式的类型含义不明确，组件拒绝同时指定多个方式和类型。
请求发送 `Control/DeviceControl`，携带 `AlarmCmd=ResetAlarm` 及 `Info/AlarmMethod`，有类型时再携带 `AlarmType`。
设备报警通知后的复位会影响设备后续同类报警上报；组件不会在报警回调或订阅中自动复位，宿主负责处置策略。
复位改变设备报警状态，不会删除宿主保存的历史报警，也不保证故障原因已经消除。

需要设备发送关键帧时，独立调用：

```java
var received = server.requestKeyFrame(deviceId, channelId);
```

报文携带 `IFrameCmd=Send`，请求设备立即发送 IDR 帧。此入口不要求本组件已有点播会话，不主动建立媒体流，
也不等待或检测 IDR 数据；具体编码输出及厂商支持需互通验证，不能用 future 成功判断画面已恢复。

两个入口的 `deviceId` 为已注册 IPC/NVR，目标为宿主已授权的设备或通道，两者均须 20 位编码；
报警复位支持 20 位设备/通道和 10 位报警中心编码。SIP 发往注册绑定的实际 UDP/TCP 端点，目标 URI 和 XML DeviceID 使用目标编码。
所有控制共用 `maxPendingQueries` 和 `queryTimeout`，取消、超时、离线/重新绑定及关闭释放挂起事务。
返回 `CompletableFuture<Void>` 在有效 SIP 2xx 后成功，只表示接收确认；非 2xx 或超时使等待失败。
设备独立发来的 `Response/DeviceControl`（包括 `Result=ERROR`）仅确认收到，不决定这个接收确认 future 的结果。
取消或提前完成 future 不撤回已发送操作；组件不自动重试、复位或发送反向操作。业务结果需宿主另行确认。

## 拉框放大与缩小

```java
var area = new GbDragZoomCommand(640, 360, 320, 180, 640, 360);
server.dragZoomIn(deviceId, channelId, area);
server.dragZoomOut(deviceId, channelId, area);
```

请求发送标准 `DragZoomIn` 或 `DragZoomOut`，参数单位为像素：窗口长度、宽度、中心点和拉框长度/宽度均限制在
0～1,000,000。两个方法只等待 SIP 接收确认，共用设备在线校验、`maxPendingQueries`、`queryTimeout` 及关闭/离线清理；
不会修改本地点播或 PTZ 状态，也不会自动反向缩放。实际画面效果由设备决定，宿主负责授权通道和用户操作。

## 远程启动、录像与报警布防控制

平台可在完成业务授权后显式发送三类 `DeviceControl`：

```java
server.teleBoot(deviceId); // TeleBoot=Boot，设备重启
server.controlRecording(deviceId, channelId, GbRecordControlCommand.RECORD);
server.controlRecording(deviceId, channelId, GbRecordControlCommand.STOP_RECORD);
server.controlGuard(deviceId, channelId, GbGuardCommand.SET_GUARD);
server.controlGuard(deviceId, channelId, GbGuardCommand.RESET_GUARD);
```

录像操作使用 `RecordCmd=Record/StopRecord`，布防操作使用 `GuardCmd=SetGuard/ResetGuard`。这些方法只表示 SIP
2xx 已接收，设备是否实际重启、录像或改变布防状态需由宿主另行确认。所有操作共用 `maxPendingQueries`、
`queryTimeout` 和设备在线/来源校验；取消、超时、离线、重新绑定及关闭会清理等待，不撤回已发送操作，也不会自动重试。
除报警复位目标可为 10 位报警中心外，其余目标编码均为 20 位设备或通道 ID；组件不提供任意 XML 命令或自动重启/录像/布防策略。

## 设备报警上报

通过带 `GbAlarmListener` 的重载接收设备主动上报：

```java
try (var server = Gb28181Server.open(options, credentials, event -> {
    var alarm = event.alarm();
    // event.sourceDeviceId() 是已注册的来源 IPC/NVR。
    // alarm.deviceId() 是设备自报的报警通道或报警中心，宿主先核对归属和权限。
    // 宿主负责保存报警、业务幂等和后续处置；关闭时应响应线程中断。
})) {
    // 宿主运行设备接入服务。
}
```

设备主动上报使用会话外 SIP MESSAGE 中的 `Notify/Alarm`。先校验注册设备及实际 UDP/TCP 来源，再解析 XML。
接纳后返回无正文 SIP 200，并向注册来源地址发送独立的 `Response/Alarm` MESSAGE，保留收到的 XML
SN、DeviceID，`Result=OK`。应答 MESSAGE 的 SIP 目标为注册 IPC/NVR，XML 中保留自报的通道编码。
应答使用独立的内部事务序号，受共享 `maxPendingQueries`、`queryTimeout`、来源校验和设备生命周期管理。
设备拒绝或未确认应答不会撤回已接受的业务事件；应答发送失败会记录不含报警正文的错误。

`GbAlarmEvent` 包含 `sourceDeviceId`、平台接收时间 `receivedAt`（Instant）和不可变 `GbAlarm`。
报警中的 deviceId 支持 20 位设备/通道或 10 位报警中心编码；sn 为正整数，priority 为 1～4，method 为
1～7 的单一报警方式。time 保留 XML dateTime 文本及可选时区，最多 64 字符，不自动换算设备时区。
可选 description 最多 1024 字符；longitude/latitude 是有限数值，分别在 ±180/±90 范围内；缺失均为 null。
可选 Info 中的 type 为正整数 AlarmType，eventType 对应 AlarmTypeParam/EventType 的 1（进入）或 2（离开）。
未提供 Info 时这两个字段为 null；类型值保留供宿主按报警方式解释。

每个服务实例使用一个受管后台线程顺序执行回调，最多保留 `maxPendingAlarms` 个事件（包含正在执行的回调）。
回调不在 SIP/维护线程或服务锁内运行；回调的 RuntimeException 会被隔离并记录，不影响后续事件。
旧的两参数 `open` 不安装处理器；没有处理器、回调容量已满或应答事务容量已满时返回 SIP 503，不投递事件。
非法 XML 返回 400，未注册/来源不匹配返回 403，内容类型错误返回 415；报警不会刷新设备心跳。

`Result=OK` 只表示组件接收入内存处理，不保证业务成功或持久化。SIP 事务重传由协议栈处理；设备以新事务
重复上报时可能重复回调，宿主按来源、SN、报警时间与通道等业务标识去重，不能仅按 SN 假设全局唯一。
关闭会丢弃尚未执行的事件并中断正在运行的回调；监听器须合作响应中断，Java 无法强制终止忽略中断的业务代码。
本组件不缓存通道归属，也不转发报警到其他设备或实现报警历史查询。

## 报警订阅

已配置 `GbAlarmListener` 且设备完成注册后，可主动订阅报警：

```java
import com.ss.gb28181.GbAlarmSubscriptionRequest;
import java.time.Duration;

var opening = server.subscribeAlarms(deviceId, targetId,
        GbAlarmSubscriptionRequest.all(Duration.ofHours(1)));
opening.thenAcceptAsync(subscription -> {
    // 保存 subscription；报警仍交给 open 时配置的 GbAlarmListener。
    subscription.completion().whenCompleteAsync((unused, error) -> {
        // 更新宿主订阅状态；失败或设备重新注册后由宿主决定是否重新订阅。
    }, businessExecutor);
    // 业务结束时调用 subscription.stop()，等待退订结果。
}, businessExecutor);
```

`deviceId` 为已注册 IPC/NVR 的 20 位编码，`targetId` 为宿主已授权的 20 位设备/通道或 10 位报警中心编码。
订阅目标等于设备本身时允许该设备报告子通道或报警中心，宿主核对归属；显式订阅其他目标时，通知中的
DeviceID 必须与目标一致。组件不在本地按报警条件再次过滤，设备支持的筛选能力需通过互通验证。

请求构造器还可指定优先级范围、报警方式集合、报警类型及起止时间。优先级为 1～4 且起始不大于结束，
0/0 表示全部；方式集合取 1～7，复制为不可变集合，空集合表示全部，XML 按升序组合为 `1/2`。
可选类型为正整数。起止时间同时省略或同时提供，使用设备本地 `LocalDateTime` 整秒、年份 1～9999，
开始严格早于结束。租期为 1～86400 整秒；这些上限属于组件输入约束，不代表所有设备都支持相应值。

使用 `SUBSCRIBE` 携带 `Query/Alarm`，当前直连兼容配置固定为 `Event: presence`，不带 Event id。
只向设备注册的实际 UDP/TCP 地址发送，复用已有 TCP 连接，不跟随 Contact 或 Record-Route 路由。
设备成功应答须返回 Contact 和有效 Expires，且不得携带 Record-Route；收到 423 等拒绝不会自动修改条件重试。
建立 future 在有效 SIP 2xx 后成功；合法 `NOTIFY` 可能先于该应答到达并触发监听器。

`GbAlarmSubscription` 提供 `deviceId()`、`targetId()`、`callId()`、`request()`、只读 `completion()` 和
幂等 `stop()`；`close()` 非阻塞发起退订。组件在设备授予租期的 80% 自动续订，使用同一会话与递增 CSeq；
未获有效续订应答不会延长租期。订阅应答使用 `queryTimeout`，退订使用 `stopTimeout`。
初次成功后 32 秒内没有通知、租期届满、续订失败、设备离线/绑定改变或服务器关闭时，生命周期异常结束。
设备正常 `terminated` 或本地主动退订成功时正常结束。不自动恢复失败订阅。

`NOTIFY` 先校验来源、Call-ID、双 tag、Event 和递增 CSeq，再校验正文；只回复无正文 SIP 200，
不会另发 `Response/Alarm` MESSAGE。无正文状态通知不会触发报警回调。有正文时复用现有 `GbAlarmEvent`
与共享 `maxPendingAlarms` 容量，容量已满回复 503，不占用 MESSAGE 查询容量。NOTIFY 不刷新设备心跳。
正常终止通知携带的最后一条报警仍可投递；本地主动停止、取消或异常中止会抑制尚未执行的订阅回调。

退订发送会话内 `SUBSCRIBE Expires: 0`，等待成功应答或终止通知。取消/提前完成建立 future 同样触发清理；
初始应答迟到时仍尽力补发退订。结束后保留 32 秒有界清理窗口，仍占用 `maxAlarmSubscriptions` 容量；
服务器关闭立即清理本地记录并尽力退订。修改返回的 future 不会取消内部清理。

## 目录订阅与变更通知

```java
import com.ss.gb28181.GbCatalogListener;
import com.ss.gb28181.GbCatalogSubscriptionRequest;
import java.time.Duration;

GbCatalogListener listener = event -> {
    // sourceDeviceId 是已注册设备，callId 标识这一次订阅。
    for (var entry : event.notification().entries()) {
        // entry.item() 为目录字段；entry.type() 为变更类型，可能为 null。
        catalogService.accept(event.sourceDeviceId(), entry);
    }
};
var server = Gb28181Server.open(options, credentials, null, listener);
// 设备完成注册后显式调用；宿主保存订阅，并在不再需要时调用 stop()。
var opening = server.subscribeCatalog(deviceId,
        GbCatalogSubscriptionRequest.all(Duration.ofMinutes(10)));
```

四参数 `open` 的第三、第四参数分别为可选报警与目录监听器，可各自为 null；已有两/三参数入口继续可用。
订阅需要目录监听器，仅支持整个已注册 IPC/NVR 的目录。请求时间可分别省略，提供时使用设备本地
`LocalDateTime` 整秒、年份 1～9999；同时提供时开始须早于结束。租期为 1～86400 整秒。
组件发送 `Query/Catalog`，采用 `Event: Catalog;id=数字`，同一会话 id 保持不变并严格匹配通知；
不兼容仅回 `Event: presence` 的设备配置，也不实现跨平台代理路由。

`GbCatalogSubscription` 提供 `deviceId()`、`callId()`、`request()`、只读 `completion()`、幂等 `stop()` 和
非阻塞 `close()`。建立、80% 自动续订、退订、超时、离线/绑定改变清理及 32 秒有界清理窗口沿用报警订阅规则。
两类订阅共享内部 SIP 状态机，容量和回调队列独立。失败后不自动重建，关闭服务器立即清理本地资源并尽力退订。

只在匹配订阅会话的 `NOTIFY` 中接收 `Notify/Catalog`，XML 根 DeviceID 必须等于订阅设备编码。
变化类型为 `ON`、`OFF`、`VLOST`、`DEFECT`、`ADD`、`DEL`、`UPDATE`；上线/离线/故障/删除可以只有编码和事件，
新增/更新要求 Name 和 Status。当前保留 `CatalogItem` 的 DeviceID、Name、ParentID、Status 投影；其他目录字段不暴露。
一条通知只含有事件的条目时，`SumNum`、`DeviceList Num` 和实际条目数必须一致。
无 Event 的目录信息以 `type() == null` 保留，可分批且 `total()` 大于当前条目数；不把它当成新增事件或完整快照。
同条通知不允许混用有/无 Event 条目。通知保留原始顺序与重复项，不在核心中聚合、去重或更新目录缓存。
需要全量同步时宿主另外调用 `queryCatalog`，并自行协调查询与变更的先后及持久化。

每个已接纳报文投递一个 `GbCatalogEvent`（含来源设备、Call-ID、接收时间和通知正文）。回调在独立单线程上按
接纳顺序执行，异常不影响后续通知；`maxPendingCatalogNotifications` 包含排队和正在执行的通知。容量满回复
SIP 503 且不推进通知 CSeq；接纳只回复无正文 SIP 200，不发送额外 MESSAGE，也不刷新设备心跳。
空正文状态通知不调用监听器；正常终止可携带最后一批通知，本地主动停止或异常中止抑制尚未执行的回调。
关闭会丢弃排队任务并中断执行线程，宿主监听器应响应中断。确认接收不保证业务落盘，幂等与授权由宿主负责。

## 移动设备位置订阅

```java
import com.ss.gb28181.GbMobilePositionListener;
import com.ss.gb28181.GbMobilePositionSubscriptionRequest;
import java.time.Duration;

GbMobilePositionListener listener = event -> {
    for (var position : event.notification().positions()) {
        // sourceDeviceId 是已注册 IPC/NVR；position.deviceId() 由宿主核对通道归属。
        positionService.accept(event.sourceDeviceId(), position);
    }
};
var server = Gb28181Server.open(options, credentials, null, null, listener);
// 设备完成注册后显式订阅；宿主管理 server 和 subscription 的关闭。
var opening = server.subscribeMobilePosition(deviceId,
        new GbMobilePositionSubscriptionRequest(Duration.ofMinutes(10), Duration.ofSeconds(5)));
```

五参数 `open` 的后三个参数依次为报警、目录和位置监听器，分别可为 null；旧入口继续可用。
位置订阅要求已配置 `GbMobilePositionListener`，目标是已注册的 20 位 IPC/NVR 编码。
`expires` 与上报 `interval` 均为 1～86400 整秒，一参数请求构造器默认每 5 秒上报；设备实际支持的间隔需互通验证。
请求使用 `SUBSCRIBE Query/MobilePosition`，包含 `Interval`；当前直连兼容配置使用 `Event: presence` 且不带 id，
该 Event 名称是组件互通配置。只向注册来源发送，不跟随 Contact 或 Record-Route 路由。

接收 GB/T 28181-2022 的批量 `Notify/MobilePosition`：根节点必须有 `Time`、`SumNum`，根 `DeviceID`
必须等于订阅设备。`DeviceList Num` 等于实际条目数，条目数不得超过 `SumNum`，声明总数受
`maxMobilePositionItems` 限制；总数为零时可省略列表。每条位置保留 20 位 DeviceID、CaptureTime、
Longitude、Latitude，可选 Speed、Direction、Altitude、Height 缺失时为 null。当前不接收旧设备的扁平位置报文。

经纬度采用 WGS84，范围为 ±180°/±90°；速度单位 km/h，非负；方向从正北顺时针计量，范围为 [0,360)°；
Altitude 为海拔米数，Height 为距地面米数，均允许有限负值。拒绝非有限值和无效数字。
通知时间 `time()` 与采集时间 `captureTime()` 保留各自 XML dateTime 文本及可选时区，不自动转换为主机时区。
`GbMobilePositionNotification` 保留不可变列表、原始顺序及重复项；分批通知逐批交付，不聚合为完整轨迹。
组件不持久化、不去重或转换坐标。业务使用条目前由宿主核对通道归属。

`GbMobilePositionEvent` 包含 `sourceDeviceId`、`callId`、接收时间 `receivedAt` 和通知正文。
位置回调使用独立有界单线程，容量 `maxPendingMobilePositionNotifications` 包含排队和执行中的通知，
不占用报警/目录队列。容量满回复 SIP 503 且不推进通知 CSeq；接纳回复无正文 200，不发送额外 MESSAGE，
也不刷新心跳。回调异常被隔离，服务器关闭丢弃排队通知并中断执行线程，监听器应响应中断。
SIP 确认仅表示接收入内存，不保证业务成功或持久化。无正文状态通知不触发业务回调。

`GbMobilePositionSubscription` 提供 `deviceId()`、`callId()`、`request()`、只读 `completion()`、幂等 `stop()`
及非阻塞退订 `close()`。建立 future 在有效 2xx 后完成，合法早到 NOTIFY 可先触发回调；80% 租期自动续订、
退订、首个 NOTIFY 期限、离线/绑定变化清理和 32 秒有界清理窗口沿用报警订阅规则。
`maxMobilePositionSubscriptions` 包含清理项；取消建立 future 仍触发清理，迟到成功应答会尽力补发退订。
本地停止、取消或异常中止抑制未执行回调，正常终止可交付最后一批位置。失败后由宿主决定是否重新订阅。

## 录像目录检索

```java
import com.ss.gb28181.RecordQuery;
import java.time.LocalDateTime;

var query = RecordQuery.all(
        LocalDateTime.of(2026, 9, 15, 0, 0),
        LocalDateTime.of(2026, 9, 16, 0, 0));
var records = server.queryRecordInfo(deviceId, channelId, query);
records.thenAcceptAsync(items -> {
    // 展示录像文件目录；返回路径只是设备侧元数据。
}, businessExecutor);
```

时间使用设备本地时间，精确到整秒，年份为 1～9999，开始必须早于结束；组件不使用宿主默认时区进行转换。
构造 `RecordQuery` 可指定 `Type.TIME`（定时）、`ALARM`（报警）、`MANUAL`（手动）或 `ALL`。
宿主负责查询授权及通道归属。请求发给已注册 IPC/NVR 的实际信令来源，SIP 与 XML 目标均为通道编码。

返回 `CompletableFuture<List<RecordItem>>`，等待独立 RecordInfo MESSAGE 并按 SumNum 聚合；SIP 200 只代表接收。
支持分批、乱序、完全重复页及空结果，列表不可变且保留首次收到的顺序。同通道可有多条录像，按
`deviceId/filePath/startTime/endTime/type/recorderId` 去重；同一键的其他字段冲突、总数改变或超限则失败。
若设备省略可选标识字段，无法区分的重复项不会额外计数，未达到 SumNum 时由查询期限结束等待。
迟到的合法通道结果只确认接收，不重新创建查询。文件可能跨越查询时间边界，返回时间不裁剪。

`RecordItem` 的 deviceId、name、secrecy（0/1）必选；filePath、address、startTime、endTime、type、
recorderId、fileSize（非负 Long，单位字节）可选，缺失时为 null。时间保留 XML dateTime 原文及可选时区；
type 为 time/alarm/manual。name/address 最多 256 字符，filePath 1024，recorderId 128，时间 64。
格式错误或声明超限的 XML 页返回 SIP 400，查询继续等待合法响应直至期限；已接受页之间的内容/总数冲突
立即结束查询。
录像检索接口只返回元数据；历史回放通过下面的独立接口发起，组件不打开返回路径或下载文件。查询共享 MESSAGE 容量、期限和累计字节限制，另受
`maxRecordItems` 独立限制；大结果需设备分多条 MESSAGE 返回。关闭、取消、超时和设备离线释放查询资源。

## 实时点播

宿主先启动媒体接收器，再指定通道与接收目的地址：

```java
import com.ss.gb28181.GbRtpTarget;

var target = new GbRtpTarget(mediaAddress, mediaPort, GbRtpTarget.Transport.UDP);
var opening = server.play(deviceId, channelId, target);
opening.thenAcceptAsync(session -> {
    // 保存 session，业务结束时调用 session.stop()。
    session.completion().whenCompleteAsync((unused, error) -> {
        // 释放宿主的 RTP 接收器。
    }, businessExecutor);
}, businessExecutor);
// opening 异常或取消时，宿主同样需要释放接收器。
```

`deviceId` 是已注册 IPC/NVR 的 20 位编码，`channelId` 是其视频通道的 20 位编码。核心不缓存目录或验证
通道归属，宿主必须完成通道授权。`play` 的 future 只表示 SIP/SDP 建立完成，不代表收到 RTP 或视频可播。
媒体接收器、PS 解复用和输出协议由宿主负责；可使用独立
[GB28181-ZLM starter](../../simple-secret-springboot-starter/simple-secret-springboot-starter-gb28181-zlm/README.md)
自动分配与释放现有内嵌 ZLM 的 RTP 接收器。

`GbPlaySession` 提供设备/通道、Call-ID、SSRC、接收目标和 `completion()`。`stop()` 幂等发送 BYE，
返回的 future 等待停止应答或超时；`close()` 非阻塞发起停止。设备 BYE 正常结束会话；离线、注销、地址改变、
服务关闭会异常结束。服务关闭尽力发送 BYE/CANCEL 后立即释放本地信令资源，不保证设备已收到停止请求。

支持单路 `PS/90000`、payload 96，offer `recvonly` / answer `sendonly`。媒体目标可选 `UDP` 或
`TCP_PASSIVE`（媒体服务器监听、设备主动连接），与 SIP 信令使用 UDP/TCP 相互独立。不支持 TCP 主动收流。
SDP 限制为单个 video、最多 256 行、每行 1024 字符，另受 `maxMessageBytes` 限制；拒绝协议、方向、SSRC
不一致的应答。SSRC 在本服务存活会话及清理窗口内唯一，重启或多个实例时由部署隔离接收器。

点播针对直接注册设备，始终向注册实际来源发送会话消息；不会转发到 Contact 指定的其他地址、不跟随
重定向或 Record-Route。若部署 SIP 代理/级联，需要额外实现路由集支持。来源及事务字段在 SIP 栈更新事务
之前校验；设备挂断还校验双 tag 和递增 CSeq。UDP 地址校验不能抵御同源地址伪造，仍依赖宿主网络边界。

取消建立 future（包括 `orTimeout` 异常完成）会撤销未完成协商；收到 provisional 后发 CANCEL，晚到的
200 执行 ACK/BYE，重复 200 重发 ACK。结束后保留 32 秒清理窗口，窗口内仍占用会话容量及 SSRC，避免
晚到响应使设备继续推流。此组件不实现媒体到达超时或 RTP/RTCP 保活，媒体在线状态需由接收器监测。

## 历史回放与控制

宿主先打开 RTP 接收端口，传入明确的 UTC 时间范围：

```java
import com.ss.gb28181.GbPlaybackRange;
import com.ss.gb28181.GbPlaybackControl;
import java.time.Instant;
import java.time.Duration;

var range = new GbPlaybackRange(
        Instant.parse("2026-09-15T00:00:00Z"),
        Instant.parse("2026-09-15T01:00:00Z"));
var opening = server.playback(deviceId, channelId, target, range);
opening.thenAcceptAsync(session -> {
    // 保存 session；控制后等待 future 完成再发下一条。
    session.control(GbPlaybackControl.pause());
}, businessExecutor);
```

`GbPlaybackRange` 只接受整秒，支持 UNIX 秒 0～253402300799，开始严格早于结束。录像检索使用设备本地
`LocalDateTime`，回放使用 `Instant`；宿主须按已知设备时区转换，组件不猜测时区。回放使用
`s=Playback`、`u=通道:0`（所有录像类型）和 UNIX 时间戳，历史 SSRC 首位为 1。协议应答需匹配模式、
时间范围、SSRC、PS/90000、方向及传输；不实现按文件路径回放或倒放；下载使用下面的独立入口。

返回现有 `GbPlaySession`，`playbackRange()` 返回历史回放或下载范围，实时会话返回空 Optional。历史回放复用
实时点播的容量、建立超时、取消、BYE、32 秒清理窗口及 RTP 资源所有权约定。

| 控制 | 调用 | 说明 |
| --- | --- | --- |
| 暂停 | `session.control(GbPlaybackControl.pause())` | 停在当前位置，保留会话与接收器 |
| 恢复 | `session.control(GbPlaybackControl.resume())` | 从暂停位置按原速恢复 |
| 定位 | `session.control(GbPlaybackControl.seek(Duration.ofSeconds(100)))` | 从本次回放起点算起的整秒偏移，非负且小于回放总时长 |
| 倍速 | `session.control(GbPlaybackControl.speed(2))` | 支持 0.25、0.5、1、2、4 倍速，不改变当前位置 |

控制使用会话内 INFO/MANSRTSP，仅允许已建立的历史会话。每会话最多一个待确认控制，复用 `queryTimeout`；
新控制在上一条结束前被拒绝，宿主应合并连续拖动事件。INFO 不占用 MESSAGE 的 `maxPendingQueries`，
其数量受历史会话数限制。控制 future 成功表示设备接收确认，不确认播放器画面已切换；SIP 2xx 无正文兼容
为接收成功，有正文时校验 MANSRTSP 状态及 CSeq。普通控制拒绝或超时仅结束该控制，481 表示会话失效。
取消或调用方完成控制 future 只停止等待，不撤回设备已执行的动作。

校验会话身份、来源、通道和序号后，`MediaStatus/NotifyType=121` 触发接收确认及 BYE；停止应答成功后
`completion()` 正常完成。重复通知不会重复发送 BYE。设备不发送结束通知时，宿主应根据媒体状态主动停止；
本组件不按墙上时钟推算回放结束，暂停和倍速会改变实际播放时长。

## 录像下载信令

```java
import com.ss.gb28181.GbDownloadRequest;

// range 与历史回放相同，使用明确的 Instant 整秒起止时间。
var request = new GbDownloadRequest(range, 2);
var opening = server.download(deviceId, channelId, target, request);
opening.thenAcceptAsync(session -> {
    var requested = session.downloadRequest();
    var declaredBytes = session.downloadFileSize();
    // 宿主接收 RTP、保存媒体并确认文件完整性。
    // 保存 session；需要中止传输时调用 session.stop()。
}, businessExecutor);
```

`GbDownloadRequest.normal(range)` 使用 1 倍速。下载速度为整数，本实现接受 1～128；这是组件的输入范围，
设备支持的具体速度由其能力决定。下载发送 `s=Download`、`u=通道:0`、UNIX 时间范围和
`a=downloadspeed:N`，支持 UDP 或 TCP_PASSIVE 接收 PS/RTP，SSRC 首位为 1。SIP 应答必须匹配下载模式、
时间范围、媒体方向/传输和 SSRC。可选 `a=filesize` 是设备声明的非负字节数，0 是有效值；缺失为
`OptionalLong.empty()`。格式错误、重复或溢出的大小声明使协商失败并清理已接受的会话。

下载复用 `GbPlaySession`。`downloadRequest()` 仅在下载会话中有值，`downloadFileSize()` 仅反映设备
SDP 声明，不代表已收到或保存的字节数。下载不能调用历史回放的 `control(...)`；中止使用 `stop()`。
实时、回放和下载共同受 `maxPlaySessions` 限制，沿用建立/停止超时、来源校验、取消与 32 秒清理窗口。
经过会话校验的 `MediaStatus/121` 会触发确认及 BYE，完成语义与历史回放一致。

此入口完成 SIP 下载协商和结束处理。宿主必须先启动 RTP 接收端，负责 PS/RTP 接收、文件写入、媒体保活
（标准附录 K）、进度和完整性校验；`completion()` 只表示信令结束，不保证末尾媒体已经排空或文件已落盘。
单纯信令不提供下载进度百分比、断点续传、HTTP 文件地址或自动本地录制。RecordInfo 返回的路径不会被访问。

## 容量、超时与资源所有权

| 选项 | 默认值 | 支持范围 |
| --- | --- | --- |
| port | 5060 | 1～65535，UDP/TCP 同端口 |
| registrationTtl | 1 天 | 1 秒～30 天，整秒；截断设备申请的更长周期 |
| heartbeatInterval | 60 秒 | 100 毫秒～1 小时 |
| heartbeatMisses | 3 | 1～100 |
| queryTimeout | 10 秒 | 50 毫秒～10 分钟，MESSAGE、回放 INFO 和报警/目录/位置订阅应答期限 |
| inviteTimeout | 10 秒 | 100 毫秒～2 分钟 |
| stopTimeout | 5 秒 | 100 毫秒～30 秒 |
| maxPlaySessions | 256 | 1～9999，实时/历史/下载共享，包含 32 秒清理窗口 |
| maxDevices | 1000 | 1～10000 |
| maxPendingQueries | 256 | 1～10000，MESSAGE 查询、PTZ/预置位/报警复位/关键帧控制接收确认及报警应答共享 |
| maxPendingAlarms | 256 | 1～10000，等待执行及正在执行的报警回调总上限 |
| maxAlarmSubscriptions | 256 | 1～10000，报警订阅及停止/中止清理项总上限 |
| maxCatalogSubscriptions | 256 | 1～10000，目录订阅及清理项总上限 |
| maxPendingCatalogNotifications | 256 | 1～10000，等待及正在执行的目录通知回调总上限 |
| maxMobilePositionSubscriptions | 256 | 1～10000，位置订阅及清理项总上限 |
| maxPendingMobilePositionNotifications | 256 | 1～10000，等待及正在执行的位置通知回调总上限 |
| maxMobilePositionItems | 1000 | 1～10000，单条位置通知声明的条目数上限 |
| maxCatalogItems | 10000 | 1～100000，目录查询及单条目录通知声明的条目数上限 |
| maxRecordItems | 10000 | 1～100000，单次录像检索独立上限 |
| maxMessageBytes | 65536 | 4096～1048576；TCP SIP 整帧与业务正文上限 |

UDP 解析前队列固定 256 个数据报，满时丢弃，由 SIP 重传恢复；TCP 每连接固定 4 KiB 预读缓冲，
不建立无界字节队列。TCP 连接数受 maxDevices 限制，出站连接超时 3 秒、写入超时 10 秒。
UDP 队列适配需要访问固定 RI 版本的内部字段，访问失败将使启动失败；不会降级到无界队列。

单次查询累计正文最多 `maxMessageBytes × 16` 字节（包括重复页），与条目上限同时生效。
XML 禁止 DTD/外部实体，深度最多 32、元素最多 2048，目录 Name 最多 256 字符，支持 GB2312/UTF-8 声明。
UDP 受单个数据报约 64 KiB 限制；大目录应分多条 MESSAGE，或使用 TCP。

组件拥有 SIP listener、连接、事务、定时器、报警/目录/位置回调线程和挂起查询。`close()` 幂等，停止服务后拒绝发起查询；未完成
future 异常结束。取消查询会释放其容量和事务。启动失败会尝试清理所有已创建资源，原始异常保留清理失败信息。
future 的普通 continuation 可能在 SIP/维护线程执行，业务处理必须使用宿主异步执行器，不能阻塞这些线程。

## 验证与标准依据

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -pl simple-secret-plugins/simple-secret-plugin-gb28181 -am test
```

IPv6 TCP 用独立 JDK 双端回环探测确认环境可用，环境将 `::1` 重定向时该参数化测试明确跳过；
IPv6 TCP 仍需在无重定向的目标网络执行互通验证。

JUnit 回环模拟设备覆盖 UDP/TCP 鉴权、来源绑定、分批目录、超时、容量、取消、注销和关闭。
标准依据：GB/T 28181-2022 §9.1/9.2/9.3/9.4/9.5/9.6/9.7/9.8/9.9/9.11、附录 A/B/D/G/I/J/L/M。测试没有连接真实设备。
上线前还需使用目标 IPC/NVR 验证厂商报文、NAT 连接复用、设备重启和长期负载。

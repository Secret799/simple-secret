# Simple Secret EasyMedia WebRTC Gateway

> 从 honeybee 的 `honeybee-module-easy-media` 迁移而来（`com.secret.honeybee.module.easymedia` → `com.ss.easymedia`），去除 Sa-Token 鉴权、hutool 依赖与 honeybee-common 模块耦合，改用通用 Redisson 依赖。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-easymedia</artifactId>
</dependency>
```

未使用 BOM 时指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-easymedia</artifactId>
    <version>1.1.0</version>
</dependency>
```

模块会传递依赖 zlm4j starter。HTTP 控制器运行时还需要宿主应用显式引入
`spring-boot-starter-web` 和 `spring-boot-starter-validation`；多实例会话和限流需要宿主按需引入
`redisson-spring-boot-starter`。

## 架构说明

Easy Media 的 WebRTC 网关处理 WHIP/WHEP 信令：鉴权、限流，以及按配置选择内嵌 ZLM C API 或外置 ZLM HTTP 上游。ICE、DTLS、SRTP 媒体数据由客户端直接连接 ZLMediaKit，不经过 Java 转发。

支持两种信令模式：

- `local-zlm-enabled: true`：调用当前服务内嵌 ZLM 的 `mk_webrtc_get_answer_sdp`。WHIP 映射为 `push`，WHEP 映射为 `play`，媒体地址为 `rtc://__defaultVhost__/{app}/{stream}`。此模式只创建 SDP Answer，不提供受管会话的 Location、PATCH 或 DELETE。
- `local-zlm-enabled: false`：向 `signaling-base-url` 指向的外置 ZLM WHIP/WHEP HTTP API 转发。该模式保留 Redis 映射、公开 Location 和会话生命周期管理。

内嵌模式下，客户端必须在发送 Offer 前等待 ICE gathering 完成；当前 C API 信令路径不支持 Trickle ICE PATCH。

播放 Edge 集群、动态调度和按需拉流不在当前阶段范围内。

## 对外接口

### 创建 WHIP 推流会话

```http
POST /easyMedia/api/webrtc/whip?app=live&stream=cam-01
Content-Type: application/sdp
Accept: application/sdp
```

成功返回 `201 Created`、SDP Answer 和统一 Location：

```http
Location: /easyMedia/api/webrtc/sessions/{sessionId}
```

### 创建 WHEP 播放会话

```http
POST /easyMedia/api/webrtc/whep?app=live&stream=cam-01
Content-Type: application/sdp
Accept: application/sdp
```

### Trickle ICE / ICE Restart

当前内嵌 ZLMediaKit 不支持会话 PATCH，因此默认返回 `405 Method Not Allowed`。只有把 `trickle-ice-enabled` 显式设为 `true`，并将 `signaling-base-url` 指向已验证支持 PATCH/ETag 的兼容上游时，网关才会代理：

```http
PATCH /easyMedia/api/webrtc/sessions/{sessionId}
Content-Type: application/trickle-ice-sdpfrag
If-Match: "<etag>"
```

不要对当前内嵌 ZLM 打开此开关。

### 终止会话

```http
DELETE /easyMedia/api/webrtc/sessions/{sessionId}
```

DELETE 是幂等操作。它只关闭指定 WebRTC 会话，不关闭整个媒体源。

旧接口 `/easyMedia/api/webrtc/sdp` 和 `/easyMedia/api/webrtc/deleteWebrtc` 已停止提供非受管会话能力，返回 `410 Gone`。

## 会话存储和故障行为

Redis 保存公开 sessionId 到内部 ZLM Location 的短期映射，不保存完整 SDP、外部 Bearer Token、ICE 密码或完整 Candidate。ZLM Location 自身包含用于精确 DELETE 的内部能力令牌，必须把 Redis 和 ZLM 管理网络按敏感控制面保护，禁止记录或对外返回该 Location。

- Redis 不可用时，新建 WHIP/WHEP 会话 fail-closed，返回服务不可用。
- ZLM 已创建但 Redis 写入失败时，网关会立即尝试 DELETE 上游真实会话。
- 上游 DELETE 暂时失败时，会话进入 CLOSING 状态并按指数退避重试。
- 客户端未发送 DELETE 时，ZLM 的 ICE/DTLS 超时负责释放 transport；Redis TTL 只清理网关映射，不会主动删除仍存活的 ZLM transport。`session-ttl` 必须大于业务允许的最长会话时长，否则会话仍可能播放但失去 PATCH/DELETE 路由能力。

> 未配置 `RedissonClient` 时，会话仓储回退到单机内存实现（`InMemoryWebRtcSessionRepository`），限流器回退到有界固定窗口实现（`InMemoryWebRtcRateLimiter`）。该模式仅适用于单实例部署，不提供跨节点会话一致性与分布式限流。生产多实例部署请引入 `redisson-spring-boot-starter` 提供 `RedissonClient`。

## 配置

配置前缀为 `simple-secret.easymedia.webrtc`：

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `enabled` | `true` | 是否启用统一会话网关 |
| `local-zlm-enabled` | `false` | 当前服务是否使用内嵌 ZLM C API 创建 WHIP/WHEP；开启后不使用 `signaling-base-url` |
| `signaling-base-url` | `http://127.0.0.1:${simple-secret.zlm4j.http-port:7080}` | 外置 ZLM WHIP/WHEP HTTP 地址；仅 `local-zlm-enabled=false` 时使用 |
| `connect-timeout` | `2s` | Java 连接 ZLM 超时 |
| `request-timeout` | `8s` | ZLM 信令读取超时 |
| `session-ttl` | `1h` | ACTIVE 会话 Redis TTL |
| `closing-ttl` | `5m` | CLOSING 会话 Redis TTL |
| `cleanup-interval` | `PT10S` | 关闭补偿执行间隔，使用 ISO-8601 Duration |
| `cleanup-initial-delay` | `PT30S` | 应用启动后的首次补偿延迟 |
| `cleanup-batch-size` | `100` | 单次补偿最大会话数 |
| `max-sdp-bytes` | `65536` | SDP/SDP Fragment 最大字节数，Controller 以有界流方式读取 |
| `trickle-ice-enabled` | `false` | 是否允许代理 PATCH；本地 C API 模式必须保持 `false` |
| `public-session-base-path` | `/easyMedia/api/webrtc/sessions` | 对外 Location 路径 |
| `security.authentication-required` | `true` | 是否要求认证身份 |
| `security.default-subject` | *(空)* | 未接入统一认证时使用的固定主体标识，配置后所有请求以此身份认证 |
| `security.default-tenant-id` | `000000` | 固定主体所在租户标识 |
| `rate-limit.enabled` | `true` | 是否启用信令限流 |
| `rate-limit.publish-per-minute` | `60` | 每主体每分钟 WHIP 创建数 |
| `rate-limit.play-per-minute` | `120` | 每主体每分钟 WHEP 创建数 |
| `rate-limit.session-operation-per-minute` | `300` | 每主体每分钟 PATCH/DELETE 数 |
| `rate-limit.key-ttl` | `10m` | 限流 Key TTL |
| `rate-limit.local-max-keys` | `10000` | 无 Redis 时本地限流器允许保留的最大键数，超限时拒绝新维度 |

默认 `WebRtcAccessPolicy` 只验证认证状态和会话所有权。业务系统需要设备、租户或流级 ACL 时，应注册自定义 `WebRtcAccessPolicy` 与 `WebRtcIdentityProvider` Bean 替换默认实现（模块不依赖任何认证框架）。

本地 C API 模式成功响应没有 `Location`。`PATCH /sessions/{sessionId}` 和 `DELETE /sessions/{sessionId}` 均返回 `405 WEBRTC_LOCAL_ZLM_SESSION_OPERATION_UNSUPPORTED`。

## 网络要求

`simple-secret.zlm4j.rtc-host=127.0.0.1` 只适用于本机测试。生产环境必须确保 ZLM SDP 中的 ICE Candidate 对客户端可达，并开放对应 RTC UDP 端口；复杂 NAT 场景需要规划 STUN/TURN。

ZLM 管理 HTTP 地址应仅在内网或本机可达。网关不转发外部 Authorization、Cookie、Host、Forwarded 等请求头，并拒绝指向非受信 ZLM authority 的 Location，防止形成 SSRF 代理。

浏览器部署还必须在可信反向代理配置明确的 Origin 允许列表。

## 指标

指标只使用低基数标签，前缀为 `simple-secret.webrtc.*`：

- `simple-secret.webrtc.session.create`
- `simple-secret.webrtc.session.mutation`
- `simple-secret.webrtc.cleanup.retry`

指标标签不包含 sessionId、app、stream、租户、用户、IP 或内部 URL。

## ZLM 通用管理 API

`ApiController`（`/easyMedia/api/common/*`）提供拉流/推流代理、流操作、录像、RTP、截图、转码与拼接屏接口，直接返回业务值（不再包裹 honeybee 的 `Result`）。失败时抛出 `ZlmOperationException`，由 Spring 默认转为 HTTP 500。

EasyMedia 和管理 API 均默认关闭。开启管理 API 后仍必须提供 `EasyMediaManagementAuthorizer` Bean；没有自定义授权器时所有请求返回拒绝。外部媒体 URL 与录像路径继续受 zlm4j 的 `resource-policy` 约束。

## H.264 裸流推送

`H264NakedFlowPushZlmManager` 不是默认 Bean，需要由宿主显式创建：

```java
@Bean(destroyMethod = "close")
H264NakedFlowPushZlmManager h264PushManager(
        ZlmMediaProperties properties) {
    return new H264NakedFlowPushZlmManager(properties);
}
```

默认队列容量为 150。自定义 `processQueueSize` 时必须大于 0，不支持无界队列。队列满时 `push` 会阻塞形成背压；对应流停止后，等待写入会快速失败，不会永久阻塞。单个未完成 NALU 最多累积 16 MiB，超过上限会丢弃该不完整 NALU 并继续处理后续数据。

```java
h264PushManager.push("live", "camera-01", h264Bytes);
h264PushManager.stopPush("live", "camera-01");
```

## UDP 组播

```java
udpMulticastManager.joinGroup(
        "239.10.10.10",
        5000,
        "192.168.1.20",
        packet -> process(packet.getData(), packet.getOffset(), packet.getLength()));
```

组播地址和本地单播地址必须使用同地址族的数字 IP，本地地址必须绑定在部署主机网卡上；端口和 handler 也会在构造时校验。`setMaxMessageLength` 只接受 1 到 65507 字节，并且只能在线程启动前调用，非法配置不会延迟到监听线程中失败。

## 依赖与测试

模块依赖 `simple-secret-springboot-starter-zlm4j`（提供内嵌 ZLM 能力）、`spring-web` 与 `micrometer-core`。`org.redisson:redisson` 为 optional，只有宿主应用需要分布式 WebRTC 会话和限流时才需引入。模块自身不引入 lombok、hutool、Sa-Token。

> 模块只声明 `spring-web`（提供 `@RestController` 等注解与 `RestClient`）。`/easyMedia/api/*` 控制器在运行时需要 Web MVC 环境，调用方需引入 `spring-boot-starter-web`（或等价 Web 栈），否则控制器不会被注册。

```bash
# 只跑本模块测试
mvn -pl simple-secret-springboot-starter/simple-secret-springboot-starter-easymedia test
```

测试不依赖真实 Redis、ZLM、浏览器或网络：Redisson 实现通过 Mockito mock `RedissonClient` 覆盖。

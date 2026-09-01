# 大疆 Camera Spring Boot Starter

`simple-secret-springboot-starter-dji` 将大疆视频接入所需的 SEI 解析、事件发布和 WebRTC 接入能力封装为可复用的 Spring Boot starter。

模块提供：

- H.264/H.265 SEI 解析与资源上限保护
- 每条完整 SEI 消息发布 `DjiSeiPacketParsedEvent` Spring 应用事件
- EasyMedia RTMP 视频轨道回调
- Servlet Web 应用中的 SEI SSE 实时诊断接口
- 复用 EasyMedia starter 的 WHIP/WHEP WebRTC 网关

宿主应用添加依赖：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-dji</artifactId>
</dependency>
```

启用 starter：

```yaml
simple-secret:
  dji-sei:
    enabled: true
    allowed-app: live
```

推流与播放端点仍由 EasyMedia 提供，例如：

- WHIP 推流：`/easyMedia/api/webrtc/whip?app=live&stream=test`
- WHEP 播放：`/easyMedia/api/webrtc/whep?app=live&stream=test`

Servlet Web 应用启用 starter 后，可按 EasyMedia 的 `app` 和 `stream` 订阅诊断事件：

```text
GET /easyMedia/api/sei/events?app=live&stream=test
```

接口返回 `text/event-stream`，发送 `snapshot`、`stream`、`stats`、`sei`、`issue` 和
`heartbeat` 事件，并设置 `Cache-Control: no-store` 与 `X-Accel-Buffering: no`。

也可以把 RTMP stream ID 作为设备 ID，订阅只包含设备 SEI 数据的事件流：

```text
GET /easyMedia/api/sei/devices/test/events
```

业务代码可以监听完整 SEI payload：

```java
@EventListener
public void onSeiPacket(DjiSeiPacketParsedEvent event) {
    byte[] completePayload = event.getPayload();
}
```

Spring 应用事件默认同步分发，监听器应快速返回；耗时处理应转交受控线程池或使用已配置执行器的 `@Async`。

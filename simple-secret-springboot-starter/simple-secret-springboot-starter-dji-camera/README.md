# 大疆 Camera Spring Boot Starter

`simple-secret-springboot-starter-dji-camera` 将大疆视频接入所需的 SEI 解析和 WebRTC 诊断能力封装为可复用的 Spring Boot starter。

模块提供：

- H.264/H.265 SEI 解析与资源上限保护
- EasyMedia RTMP 视频轨道回调
- 最近 10 条完整 SEI 消息的 SSE 实时输出
- `/easyMedia/api/sei/events` 诊断接口
- 复用 EasyMedia starter 的 WHIP/WHEP WebRTC 网关

宿主应用添加依赖：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-dji-camera</artifactId>
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
- SEI 事件：`/easyMedia/api/sei/events?app=live&stream=test`

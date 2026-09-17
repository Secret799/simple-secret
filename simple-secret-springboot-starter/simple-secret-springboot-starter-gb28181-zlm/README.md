# Simple Secret GB28181-ZLM Starter

`simple-secret-springboot-starter-gb28181-zlm` 为 GB28181 实时点播、历史回放与下载提供内嵌 ZLMediaKit RTP 接收器。它依赖
GB28181 starter 与 ZLM4J starter，默认关闭，不会隐式启用 SIP 监听或加载 ZLM 原生库。

## 依赖与配置

导入 Simple Secret BOM 后声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-gb28181-zlm</artifactId>
</dependency>
```

宿主必须分别按 [GB28181 文档](../simple-secret-springboot-starter-gb28181/README.md) 配置设备凭据与信令服务，
按 [ZLM4J 文档](../simple-secret-springboot-starter-zlm4j/README.md) 提供平台原生库并显式启用 ZLM。适配器也
支持宿主直接提供 `Gb28181Server` 与 `IZlmMediaService` Bean；启用适配器却缺少依赖时启动失败。

```yaml
simple-secret:
  gb28181-zlm:
    enabled: true
    advertised-address: 192.0.2.1 # 替换为设备可访问的媒体接收地址
    transport: udp
    max-sessions: 256
```

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `false` | 是否创建默认 `GbZlmPlayer` |
| `advertised-address` | 无 | 必填数字 IP，写入 SDP；不改变 ZLM 本地监听地址 |
| `transport` | 无 | 必填 `udp` 或 `tcp-passive`；后者由设备主动连接 ZLM |
| `max-sessions` | `256` | 分配中及活动接收器总上限，范围 1–10000；同时限制工作队列 |

媒体传输与 SIP 的 UDP/TCP 独立。ZLM 使用随机端口，部署需允许相应 RTP 端口通信；NAT 映射和防火墙由宿主管理。

## 实时点播

```java
import com.ss.gb28181.zlm.GbZlmPlaySession;
import com.ss.gb28181.zlm.GbZlmPlayer;

player.play(deviceId, channelId).thenAcceptAsync(session -> {
    String app = session.app();       // rtp
    String stream = session.stream(); // 每次请求唯一
    // 宿主根据 ZLM 输出协议、域名和鉴权配置构造播放 URL。
    // 保存 session，在业务结束时调用 session.stop() 或 session.close()。
});
```

`play` 先打开 RTP 接收端口，再发出 SIP INVITE。返回的 future 仅表示 SIP/SDP 协商完成，不保证已经收到媒体、
转封装完成或播放器可播。设备与通道编码均为 20 位；通道归属、访问授权及播放鉴权由宿主负责。
`GbZlmPlaySession` 提供 `app()`、`stream()`、`sipSession()`、`stop()`、`completion()` 和非阻塞 `close()`。

取消尚未完成的 future 会取消信令建立并释放 RTP；拒绝、超时、设备挂断或本地停止也会释放接收器。单线程有界
工作队列执行 native 分配与释放，SIP completion 不等待 native；业务 continuation 应使用自己的有界执行器。

默认 player 由 Spring 管理，销毁时拒绝新请求、取消等待中的 INVITE、停止已建立会话，并等待本地 RTP 清理，
不等待 BYE 响应。等待上限为 5 秒；native 清理失败或超时会抛出异常，未释放资源仍被保留，可再次 `close()`
重试。native 调用不能被 Java 强制中止，阻塞调用会留在 daemon 工作线程中。正常关闭后不再保留工作线程。
宿主覆盖 `GbZlmPlayer` Bean 时默认实现退让，自定义 Bean 的销毁方式由宿主声明。

## 历史回放

```java
import com.ss.gb28181.GbPlaybackRange;
import com.ss.gb28181.GbPlaybackControl;

player.playback(deviceId, channelId, range).thenAcceptAsync(session -> {
    // range 为使用 Instant 整秒起止时间的 GbPlaybackRange。
    session.sipSession().control(GbPlaybackControl.pause());
}, businessExecutor);
```

回放复用相同 RTP 分配、容量及释放流程；暂停不会释放接收器。通过 `sipSession().control(...)` 恢复、
定位或调速，等待上一条控制确认后再发送下一条。标准媒体结束通知、停止或会话异常会触发接收器清理。
时间范围及支持的控制见 [核心文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md#历史回放与控制)。
SIP 回放成功仍不表示视频可播，历史流的实际输出和播放器跳转效果需真实设备与 ZLM 联调。

## 下载接收适配

```java
import com.ss.gb28181.GbDownloadRequest;

player.download(deviceId, channelId, GbDownloadRequest.normal(range))
        .thenAcceptAsync(session -> {
            var declaredBytes = session.sipSession().downloadFileSize();
            // 获取 stream 标识并交给宿主媒体处理；需要中止时调用 session.stop()。
        }, businessExecutor);
```

与实时/历史请求共用接收器上限、分配失败清理、取消和结束处理。下载模式不接受 `sipSession().control(...)`。
该适配器只分配 RTP 接收器并协调信令，不自动启动录制或创建下载文件；宿主负责配置媒体保存、保活、
尾部数据排空及文件完整性校验。设备声明大小和 SIP completion 均不能替代文件保存完成的判断。
具体协议与限制见 [核心文档](../../simple-secret-plugins/simple-secret-plugin-gb28181/README.md#录像下载信令)。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-gb28181-zlm -am test
```

单元测试使用信令/媒体接口替身验证编排、容量、失败、取消及关闭；独立 `consumer-gb28181-zlm` 验证已安装制品
的 API、自动配置 imports、配置元数据及默认关闭行为，不加载 native。真实设备、PS/RTP 兼容性、NAT 和 ZLM
媒体输出仍需在目标部署环境验证。

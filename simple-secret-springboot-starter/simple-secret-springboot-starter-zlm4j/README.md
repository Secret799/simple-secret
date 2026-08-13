# Simple Secret ZLM4J Starter

`simple-secret-springboot-starter-zlm4j` 面向 Java 17 和 Spring Boot 3.5，通过 `com.aizuda:zlm4j` 的 JNA 绑定在当前 JVM 中运行 ZLMediaKit，提供推拉流代理、流查询、MP4/TS 录像、RTP 服务、统计、服务器配置、FFmpeg 转码和截图。

模块只有显式开启时才初始化原生媒体服务。它不依赖 JSON starter、Honeybee、Hutool、Guava 或 Apache Commons Configuration；内置 INI 配置由 JDK API 解析。Lombok 仅用于兼容迁移过来的 zlm4j 数据对象，第三方业务代码不需要使用 Lombok API。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-zlm4j</artifactId>
</dependency>
```

未使用 BOM 时指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-zlm4j</artifactId>
    <version>1.1.0</version>
</dependency>
```

## 原生运行前提

部署环境必须提供与操作系统和 CPU 架构匹配的 ZLMediaKit 原生库 `mk_api`。截图、转码和视频拼接还依赖 JavaCPP FFmpeg native。

starter 项目中的 Maven OS profile 只在构建本项目源码时生效，不会传递给第三方消费项目。宿主应用必须按平台显式增加 runtime classifier。Linux x86-64 示例：

```xml
<dependencies>
    <dependency>
        <groupId>org.bytedeco</groupId>
        <artifactId>javacpp</artifactId>
        <version>1.5.10</version>
        <classifier>linux-x86_64</classifier>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.bytedeco</groupId>
        <artifactId>ffmpeg</artifactId>
        <version>6.1.1-1.5.10</version>
        <classifier>linux-x86_64-gpl</classifier>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

其他支持的 classifier 为 `linux-arm64`、`windows-x86_64`、`macosx-arm64`、`macosx-x86_64`，FFmpeg classifier 均需追加 `-gpl`。只使用不经过 FFmpeg 的媒体服务接口时无需额外引入这些 classifier，但 `mk_api` 仍是必需项。

## 最小配置

ZLM 默认关闭，匿名播放和匿名推流默认拒绝：

```yaml
simple-secret:
  zlm4j:
    enabled: true
    listen-ip: 127.0.0.1
    allow-anonymous-play: false
    allow-anonymous-publish: false
    root-path: ./www
    thread-num: 5
    rtmp-port: 7935
    rtsp-port: 7554
    http-port: 7080
    rtc-port: 8000
```

ZLM 启动时以原生 `mk_ini_default()` 为基础，再应用 `ZlmMediaProperties` 暴露的配置字段。内置 `simple-secret__zlm4j-default__conf.ini` 仅用于内部默认配置加载，不是任意 INI 键的透传入口。第三方应用应只使用配置元数据中存在的 `simple-secret.zlm4j.*` 属性。

启用前应确认端口未被占用。Spring 容器关闭时 starter 会停止 ZLM 服务并释放已管理的上下文资源。

## 拉流代理和在线状态

自动配置会提供 `IZlmMediaService`：

```java
import com.ss.zlm4j.service.IZlmMediaService;
import com.ss.zlm4j.service.domain.bo.MediaQueryBO;
import com.ss.zlm4j.service.domain.bo.StreamProxyPullerBO;
import org.springframework.stereotype.Service;

@Service
public class CameraStreamService {
    private final IZlmMediaService mediaService;

    public CameraStreamService(IZlmMediaService mediaService) {
        this.mediaService = mediaService;
    }

    public String startCamera(String rtspUrl) {
        StreamProxyPullerBO request = new StreamProxyPullerBO()
                .setApp("live")
                .setStream("camera-01")
                .setUrl(rtspUrl)
                .setRetryCount(-1)
                .setRtpType(0);
        return mediaService.addStreamPullerProxy(request);
    }

    public boolean isOnline() {
        MediaQueryBO query = new MediaQueryBO()
                .setApp("live")
                .setStream("camera-01")
                .setSchema("rtsp");
        return Boolean.TRUE.equals(mediaService.isMediaOnline(query));
    }

    public void stopCamera(String proxyKey) {
        mediaService.delStreamPullerProxy(proxyKey);
    }
}
```

`addStreamPullerProxy` 和 `addStreamPusherProxy` 只有在 native 首次连接成功后才返回代理 key，业务侧应保存该
key 以便精确删除代理。首次连接失败、等待超过 5 秒或等待线程被中断时会抛出
`ZlmOperationException`，并从注册表移除代理、回调及释放 native 资源，不会把错误文本或 `null` 当作成功
结果返回。相同 key 被重新使用时，旧代理的延迟关闭回调不会删除新代理的回调。
服务开始关闭后会永久拒绝创建新的推流、拉流和 RTP 资源。关闭时会尝试释放全部已注册资源；某个 native
资源释放失败不会阻止其他资源清理，失败资源会保留在注册表中，调用方可再次执行 `close()` 重试。

不要把未验证的客户端 URL 直接传入服务；业务层仍需校验租户、设备归属和允许的媒体源。

## 录像、RTP、截图和转码

```java
import com.ss.zlm4j.service.ISnapService;
import com.ss.zlm4j.service.ITranscodeService;
import com.ss.zlm4j.service.IZlmMediaService;
import com.ss.zlm4j.service.domain.bo.OpenRtpServerBO;
import com.ss.zlm4j.service.domain.bo.StartRecordBO;
import com.ss.zlm4j.service.domain.bo.TranscodeBO;

StartRecordBO record = new StartRecordBO()
        .setApp("live")
        .setStream("camera-01")
        .setType(1)
        .setCustomizedPath("records/camera-01")
        .setMaxSecond(300L);
boolean recording = Boolean.TRUE.equals(mediaService.startRecord(record));

int rtpPort = mediaService.openRtpServer(new OpenRtpServerBO()
        .setPort(0)
        .setTcpMode(0)
        .setStream("rtp-camera-01"));

String jpegBase64 = snapService.snapToBase64(
        "https://media.example/live/camera-01.live.ts");

transcodeService.transcode(new TranscodeBO()
        .setUrl("rtsp://camera.example/live")
        .setApp("transcoded")
        .setStream("camera-01")
        .setEnableAudio(true)
        .setScaleWidth(1280)
        .setScaleHeight(720));
```

上例中的 `mediaService`、`snapService`、`transcodeService` 分别为注入的 `IZlmMediaService`、`ISnapService`、`ITranscodeService`。业务停止时应对应调用 `stopRecord`、`closeRtpServer` 或 `stopTranscode` 释放资源。

## 监听 ZLM 事件

ZLM hook 会转换为 Spring 事件：

```java
import com.ss.zlm4j.event.StreamRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StreamEventListener {
    @EventListener
    public void onStreamRegistered(StreamRegisteredEvent event) {
        String stream = event.getMediaSource().getStream();
        // 更新业务流状态。
    }
}
```

需要替换特定 hook 行为时，可注册 `ZlmCallbackHandlerRegister`，不要直接修改 starter 内部上下文。

## 资源访问和部署安全

所有外部媒体 URL 都会经过 `simple-secret.zlm4j.resource-policy` 校验。默认允许常用媒体协议，但拒绝 URL user-info、回环、私网、链路本地、多播、CGNAT 和 IPv6 ULA 地址。确需访问内网媒体源时，只开放明确主机或最小 CIDR：

```yaml
simple-secret:
  zlm4j:
    mp4-save-path: /srv/simple-secret/recordings/mp4
    hls-save-path: /srv/simple-secret/recordings/hls
    resource-policy:
      allowed-hosts:
        - camera.internal.example
      allowed-cidrs:
        - 10.20.30.0/24
      recording-root: /srv/simple-secret/recordings
```

`StartRecordBO.customizedPath` 非空时只能写入 `recording-root` 下；为空时 ZLM 使用 `mp4-save-path` 或 `hls-save-path`，这两个全局路径必须由生产配置限制在专用媒体目录。生产环境还应在网络层限制 RTSP、RTMP、HTTP 和 RTC 端口，并在业务层实现播放、推流鉴权。

FFmpeg 使用 `*-gpl` classifier，发布或分发应用前必须确认 GPL 许可义务。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-zlm4j test
```

单元测试不启动真实 ZLM 或 FFmpeg native，覆盖自动配置、配置加载、资源策略、服务生命周期、截图异常路径和事件处理。真实部署仍需在目标操作系统上验证 `mk_api`、FFmpeg classifier、端口和媒体协议连通性。

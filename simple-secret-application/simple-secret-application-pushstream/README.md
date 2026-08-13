# Simple Secret Pushstream Application

`simple-secret-application-pushstream` 是从 Honeybee pushstream 迁移并重新实现的独立开发工具应用。它周期扫描
一个受控本地目录，为新增媒体文件启动 FFmpeg 循环推流，文件更新时重启对应进程，文件删除时停止进程。

本模块不是业务系统应依赖的 starter。它只直接依赖 zlm4j starter、Spring Web 和 Validation，不直接依赖
EasyMedia、Toolbox、Hutool、Guava、Lombok 或 Simple Secret JSON starter。zlm4j starter 会传递 Toolbox，
Spring Web 会传递 Spring Boot 标准 JSON/Jackson 依赖；FFmpeg 是部署环境提供的外部可执行程序。

## 架构与流程

```mermaid
flowchart LR
    DIR["受控媒体目录"] --> SCAN["单线程周期扫描"]
    SCAN --> DIFF["文件快照差异"]
    DIFF --> PROCESS["FFmpeg 进程管理器"]
    PROCESS --> FFMPEG["每个文件一个 FFmpeg 进程"]
    FFMPEG --> ZLM["内嵌 ZLMediaKit RTSP 入口"]
    ZLM --> STATUS["在线流查询"]
    SCAN --> API["可选只读状态接口"]
    STATUS --> API
```

每轮同步按以下顺序执行：

1. 使用 `Path` 和 `Files.walk` 扫描目录，不跟随符号链接，只保留允许后缀并稳定排序；记录根目录真实路径和文件系统身份。
2. 以绝对规范化路径比较前后快照，识别新增、更新和删除文件。
3. 启动前重新核对真实路径、可用的文件身份、大小和修改时间，检测扫描后被链接或替换的文件。
4. 使用 `ProcessBuilder(List<String>)` 直接启动 FFmpeg，不创建 Shell 脚本，不使用 `nohup`、重定向或 `&`。
5. 更新文件先停止旧进程再启动新进程，删除文件停止对应进程；批量停止共享一个总退出截止时间。
6. 进程启动失败或意外退出时按 5 秒起步、最大 5 分钟的指数退避重试，防止持续 fork；
   连续健康运行 5 分钟后重置失败计数。
7. 状态查询每次最多调用一次 ZLM 媒体列表接口，不在文件循环中重复查询。

流 ID 由清理后的文件基础名和相对路径 SHA-256 短摘要组成，避免同名文件冲突和非法 URL 字符。状态接口只返回
文件名、流 ID、文件大小、修改时间、状态和脱敏错误，不返回本地绝对路径。

## 前置条件

- Java 17。
- FFmpeg 在 `PATH` 中，或通过 `ffmpeg-executable` 配置绝对路径。
- 当前平台能够加载 zlm4j 对应的 ZLMediaKit native 库。
- 扫描目录必须提前创建，必须是绝对普通目录，不能是符号链接。
- 输入媒体必须能被 FFmpeg 直接读取，并支持 `-c copy` 推送到 RTSP。编码或容器不兼容时应先离线转码。

## 配置案例

```yaml
server:
  address: 127.0.0.1
  port: 9879

simple-secret:
  zlm4j:
    enabled: true
    listen-ip: 127.0.0.1
    rtsp-port: 7554
    allow-anonymous-publish: true
  publish-stream:
    enabled: true
    status-api-enabled: true
    scan-directory: /opt/simple-secret/media
    allowed-suffixes: [mp4, mov, mkv, ts, flv, h264, h265]
    app: publish
    ffmpeg-executable: /usr/local/bin/ffmpeg
    rtsp-host: 127.0.0.1
    rtsp-port: 7554
    scan-interval: 30s
    shutdown-timeout: 5s
    max-concurrent-streams: 8
    max-scanned-files: 1000
    recursive: false
```

`publish-stream.rtsp-port` 必须与 `zlm4j.rtsp-port` 一致。推流目标默认是回环地址；只有 ZLMediaKit 监听在
受控内网地址时才应修改 `rtsp-host`。zlm4j 默认拒绝匿名推流，本应用的 FFmpeg 命令当前不附带鉴权参数，
因此本地工具模式需要允许匿名推流，并把所有 ZLM 端口限制在回环地址或隔离网络中。

## 构建与启动

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -pl \
  simple-secret-application/simple-secret-application-pushstream -am package

mkdir -p /opt/simple-secret/media

JAVA_HOME=/opt/homebrew/opt/openjdk@17 java \
  -jar simple-secret-application/simple-secret-application-pushstream/target/\
simple-secret-application-pushstream.jar \
  --simple-secret.zlm4j.enabled=true \
  --simple-secret.zlm4j.allow-anonymous-publish=true \
  --simple-secret.publish-stream.enabled=true \
  --simple-secret.publish-stream.scan-directory=/opt/simple-secret/media
```

新增文件后，最迟在一个 `scan-interval` 内启动推流。播放地址格式为：

```text
rtsp://127.0.0.1:7554/publish/<生成的-stream-id>
```

启用只读接口后可以查询：

```bash
curl http://127.0.0.1:9879/api/publish-stream/status
```

状态值包括 `STARTING`、`ONLINE`、`STOPPED` 和 `FAILED`。`mediaServerReachable=false` 表示最近一次 ZLM
状态查询失败，不再与正常注册延迟混淆。FFmpeg 异常退出时会返回脱敏退出码和退避状态，但不会返回原始命令输出或路径。
应用退出时会先取消后续调度并等待当前扫描完成，再永久关闭进程管理器并销毁全部受管 FFmpeg 进程；
全部进程共享一个 `shutdown-timeout` 截止时间，超时后仍存活的进程会被强制终止。

## 安全与运维边界

- 状态接口默认关闭，也没有内置认证。启用后只能绑定回环地址，或由宿主网关提供认证、授权和访问日志。
- 扫描目录应使用专用低权限账户，只授予读取权限；不要扫描上传暂存目录、用户主目录或系统目录。
- 应确保扫描目录及其全部祖先目录不可被非可信账户重命名或替换；模块会在每轮扫描和进程启动前复验真实路径与文件身份，降低路径替换风险，但操作系统按路径打开文件时仍存在极短竞争窗口。
- 最大并发数限制为 1 到 128，单次扫描文件数限制为 1 到 10000；必须按 CPU、磁盘吞吐和
  ZLMediaKit 容量设置，不能机械使用上限。
- 应通过进程级监控观察 FFmpeg 重启频率和资源消耗。模块不会记录完整文件路径，也不会把 FFmpeg 输出写入无限日志。
- 当前实现不生成临时 MP4、不执行转码、不自动下载 FFmpeg，也不管理远程 ZLMediaKit。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -pl \
  simple-secret-application/simple-secret-application-pushstream -am verify
```

自动化测试使用虚拟进程和临时目录，不启动真实 FFmpeg、ZLMediaKit 或网络连接。

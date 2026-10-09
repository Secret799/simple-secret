# Simple Secret 大华 SDK 测试应用

`simple-secret-application-dahua-test` 是本地诊断应用：通过大华 NetSDK 登录设备做实时预览并转推到内嵌
ZLMediaKit，浏览器页面以 WebSocket-FLV 播放，同时提供云台（PTZ）同步控制的按压式操作面板。它不是可复用库，
不发布到仓库，仅用于内网环境验证 `simple-secret-plugin-camera-sdk-dahua` 和
`simple-secret-springboot-starter-camera-zlm` 的真实设备行为。

## 依赖与运行前提

- JDK 17，Linux 或 Windows（大华 NetSDK 仅提供这两个平台；macOS 无法加载 NetSDK）。
- 部署环境提供大华 NetSDK 原生库目录（Linux 为 `libdhnetsdk.so` 及其依赖），通过环境变量
  `DAHUA_NETSDK_DIRECTORY` 指定；未设置时应用仍可启动，页面会提示 SDK 未初始化。
- 设备编码须为 H.264（H.265 不会被转码，参见 camera-zlm starter 说明）。

## 启动

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17   # macOS 交叉构建时
mvn -f simple-secret-application/simple-secret-application-dahua-test/pom.xml package
DAHUA_NETSDK_DIRECTORY=/path/to/dhnetsdk java -jar \
  simple-secret-application/simple-secret-application-dahua-test/target/simple-secret-application-dahua-test.jar
```

页面地址 `http://<host>:8080/`。在 macOS 上验证时可用 amd64 容器运行（OrbStack/Rosetta）：

```bash
docker run -d --name dahua-test --platform linux/amd64 \
  -v "$PWD/simple-secret-application/simple-secret-application-dahua-test/target/simple-secret-application-dahua-test.jar":/app/app.jar:ro \
  -v /path/to/dhnetsdk-linux-x64:/opt/dhnetsdk:ro \
  -e DAHUA_NETSDK_DIRECTORY=/opt/dhnetsdk \
  -p 8080:8080 -p 7080:7080 -p 7554:7554 -p 7935:7935 \
  eclipse-temurin:17-jre java -jar /app/app.jar
```

## 页面功能

- **设备连接**：填写设备 IP、NetSDK 端口（默认 37777）、账号、密码、逻辑通道（1 起，SDK 按设备起始通道换算）
  和码流类型；点击“开始播放”后建立 NetSDK 预览并推送到 ZLM，页面自动以 ws-flv 拉流播放。
- **云台控制**：方向九宫格、变倍、焦点、光圈按钮，按住持续运动、松开停止；速度档位 1-8。
- **状态面板**：SDK 就绪、推流会话、ZLM 在线协议轮询显示，会话级异常（`session.failure()`）实时可见。
- **日志面板**：接口调用结果流水。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/config` | SDK/推流服务就绪状态与 ZLM HTTP 端口 |
| POST | `/api/stream/start` | 建立 SDK 预览并推流，body 含设备凭据与 app/stream |
| POST | `/api/stream/stop` | 关闭当前推流会话（幂等） |
| GET | `/api/stream/status` | 会话状态、ZLM 在线协议、活动会话数 |
| POST | `/api/ptz` | 云台同步控制：command + isBegin 或 duration（脉冲）+ speedLevel |
| POST | `/api/debug/frames` | 诊断：直接统计插件 realPlay 回调帧（区分回调未触发与帧被过滤） |
| POST | `/api/debug/raw` | 诊断：绕过插件，经典 `CLIENT_RealPlayEx` 原始码流回调统计（DHAV 帧形态、类型直方图） |

云台为“短脉冲”语义：按住时页面连续下发 `duration=PT0.8S` 的脉冲，每个脉冲在单次调用内完成开始与停止（设备要求同登录会话配对），松开即停止连发。

## 安全边界

- 仅限内网测试：ZLM 监听 `0.0.0.0` 且开启匿名播放，页面与接口无鉴权，不要暴露到公网。
- 设备凭据只存在于浏览器内存和请求体中，服务端不落盘、不打日志；原生库目录只从
  `DAHUA_NETSDK_DIRECTORY` 读取。
- 页面播放器 mpegts.js（Apache-2.0）以源码形式内置在 `src/main/resources/static/`。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -f simple-secret-application/simple-secret-application-dahua-test/pom.xml test
```

单元测试关闭原生能力，覆盖接口自检、参数校验、SDK 缺失路径和幂等停止；真实播放与云台需在提供
NetSDK 的环境执行。

# EasyMedia 测试应用

`simple-secret-application-easymedia-test` 是一个可直接启动的 Spring Boot 测试程序，用于验证 `simple-secret-springboot-starter-easymedia` 的内嵌 ZLMediaKit、媒体管理 API 以及 WHIP/WHEP WebRTC 能力。

应用默认只监听 `127.0.0.1:9878`。未启用 `local` profile 时，ZLM 和 EasyMedia 均保持关闭，因此可以在没有原生库的环境中完成 Spring 上下文测试。

## 运行要求

- Java 17。
- ZLMediaKit C API 动态库 `mk_api`。macOS 通常为 `libmk_api.dylib`，Linux 通常为 `libmk_api.so`，Windows 通常为 `mk_api.dll`。
- `mk_api` 及其依赖必须与当前操作系统和 CPU 架构匹配。`jna.library.path` 用于帮助 JNA 找到直接加载的 `mk_api`；`mk_api` 依赖的其他动态库仍必须由系统动态链接器通过 rpath、系统安装路径或平台库路径找到。
- Maven 会按当前平台自动选择 JavaCPP 和 FFmpeg native classifier，支持 Windows x86_64、Linux x86_64、Linux arm64、macOS arm64 和 macOS x86_64。`mk_api` 不在 Maven 依赖中，仍需由运行环境提供。

## 构建

在项目根目录执行：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
mvn -pl simple-secret-application/simple-secret-application-easymedia-test \
    -am package
```

构建产物为：

```text
simple-secret-application/simple-secret-application-easymedia-test/target/simple-secret-application-easymedia-test.jar
```

## 启动

`local` profile 会启动内嵌 ZLM 和 EasyMedia，并把运行数据写入 `./runtime/easymedia`：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
DYLD_LIBRARY_PATH=/path/to/zlmediakit/lib \
java -Djna.library.path=/path/to/zlmediakit/lib \
  -jar simple-secret-application/simple-secret-application-easymedia-test/target/simple-secret-application-easymedia-test.jar \
  --spring.profiles.active=local
```

上例适用于 macOS。Linux 使用 `LD_LIBRARY_PATH=/path/to/zlmediakit/lib`，Windows 则把该目录加入进程的 `PATH`。如果 `mk_api` 已通过 rpath 或系统安装目录正确解析，可以不设置对应的平台库路径；`jna.library.path` 仍可用于指定 `mk_api` 本身所在目录。

可以通过 `SIMPLE_SECRET_EASYMEDIA_ROOT` 修改日志、录像和其他运行文件的根目录：

```bash
SIMPLE_SECRET_EASYMEDIA_ROOT=/var/lib/simple-secret/easymedia \
DYLD_LIBRARY_PATH=/path/to/zlmediakit/lib \
java -Djna.library.path=/path/to/zlmediakit/lib \
  -jar simple-secret-application/simple-secret-application-easymedia-test/target/simple-secret-application-easymedia-test.jar \
  --spring.profiles.active=local
```

测试配置固定使用 `local-test` 租户和 `easymedia-test-user` 主体，以便直接验证 WebRTC 流程。该身份配置仅用于本地测试，不应复制到生产应用。

## WHIP/WHEP 测试

请求体必须是真实 WebRTC 客户端生成的 SDP Offer。创建 WHIP 推流会话：

```bash
curl -i \
  -X POST \
  -H 'Content-Type: application/sdp' \
  -H 'Accept: application/sdp' \
  --data-binary @offer.sdp \
  'http://127.0.0.1:9878/easyMedia/api/webrtc/whip?app=live&stream=camera-01'
```

已有 `live/camera-01` 流时，创建 WHEP 播放会话：

```bash
curl -i \
  -X POST \
  -H 'Content-Type: application/sdp' \
  -H 'Accept: application/sdp' \
  --data-binary @offer.sdp \
  'http://127.0.0.1:9878/easyMedia/api/webrtc/whep?app=live&stream=camera-01'
```

成功响应状态为 `201`，响应体是 `application/sdp` 格式的 SDP Answer。local profile 使用本地 C API 信令模式，不会返回 `Location`，也不支持 `/sessions/{sessionId}` 的 PATCH 或 DELETE 操作。

## 管理 API

管理 API 默认关闭。只在本地排查确有需要时，通过环境变量同时开启接口并设置不可为空的令牌：

```bash
SIMPLE_SECRET_EASYMEDIA_MANAGEMENT_ENABLED=true \
SIMPLE_SECRET_EASYMEDIA_MANAGEMENT_TOKEN='replace-with-a-random-token' \
DYLD_LIBRARY_PATH=/path/to/zlmediakit/lib \
java -Djna.library.path=/path/to/zlmediakit/lib \
  -jar simple-secret-application/simple-secret-application-easymedia-test/target/simple-secret-application-easymedia-test.jar \
  --spring.profiles.active=local
```

所有 `/easyMedia/api/common/*` 请求都必须携带 `X-Test-Token`。例如查询当前媒体流：

```bash
curl -i \
  -H 'X-Test-Token: replace-with-a-random-token' \
  'http://127.0.0.1:9878/easyMedia/api/common/getAllMedia'
```

令牌缺失或不匹配时返回 `403`。开启管理 API 但未配置令牌时，应用会启动失败，避免无保护地暴露管理能力。

## 自动化测试

普通单元测试和默认关闭原生能力的上下文测试：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
mvn -o -pl simple-secret-application/simple-secret-application-easymedia-test \
    -am test
```

真实 WHEP 合约测试默认跳过。先启动应用并准备已发布流和浏览器生成的 Offer SDP，再执行：

```bash
SIMPLE_SECRET_EASYMEDIA_IT=true \
SIMPLE_SECRET_EASYMEDIA_IT_BASE_URL=http://127.0.0.1:9878 \
SIMPLE_SECRET_EASYMEDIA_IT_APP=live \
SIMPLE_SECRET_EASYMEDIA_IT_STREAM=camera-01 \
SIMPLE_SECRET_EASYMEDIA_IT_OFFER_SDP=/absolute/path/to/offer.sdp \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
mvn -pl simple-secret-application/simple-secret-application-easymedia-test \
    -Dtest=WebRtcGatewayContractTest test
```

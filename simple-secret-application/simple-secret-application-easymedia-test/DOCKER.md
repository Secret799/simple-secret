# EasyMedia WebRTC HTTPS Docker 部署手册

该方案使用 Nginx 提供 HTTPS，EasyMedia 提供 WHIP/WHEP 信令，内嵌 ZLMediaKit 通过固定的 `8000/tcp` 和 `8000/udp` 传输 WebRTC 媒体。

## 1. 前置条件

- Linux 服务器已安装 Docker Engine 和 Docker Compose v2。
- 域名的 A 记录已经指向服务器公网 IPv4。
- 防火墙和云安全组放行 `80/tcp`、`443/tcp`、`1935/tcp`、`8000/tcp`、`8000/udp`。
- 已取得该域名的 PEM 格式证书和私钥。

服务器在 NAT 后时，路由器必须把上述端口映射到服务器，并保证公网和容器使用相同的 WebRTC 端口 `8000`。对称 NAT 或受限企业网络可能还需要 TURN；本 Compose 不内置 TURN 服务。

## 2. 放置证书

进入本模块目录：

```bash
cd simple-secret-application/simple-secret-application-easymedia-test
```

将证书文件放到固定位置，并保持以下文件名：

```text
docker/certs/fullchain.pem  # 完整证书链
docker/certs/privkey.pem    # 证书私钥
```

Let's Encrypt 对应文件通常就是 `fullchain.pem` 和 `privkey.pem`。若证书服务商提供 `.crt`、`.key` 或其他名称，请复制或重命名为上面的文件名。不要把私钥提交到 Git。

## 3. 配置域名

```bash
cp .env.example .env
```

编辑 `.env`，至少修改：

```dotenv
DOMAIN=webrtc.junpzx.cn
```

容器启动时会把域名解析出的第一个 IPv4 写入 WebRTC SDP。如果域名有多个 A 记录、DNS 尚未生效、经过 CDN，或者服务器位于 NAT 后，请显式填写实际接收 `8000` 端口流量的公网 IPv4：

```dotenv
WEBRTC_PUBLIC_IP=203.0.113.10
```

当前目标服务器为 netcatty 的华为云 `1.95.135.125`。DJI 设备按以下地址推送 RTMP，`device-01`
作为设备 ID，同时也是 SSE 订阅使用的 stream ID：

```text
rtmp://webrtc.junpzx.cn:1935/live/device-01
```

WebRTC 媒体端口不能通过普通 HTTP CDN 或 Nginx 转发。域名若使用 Cloudflare 等代理，必须让该 DNS 记录仅做 DNS 解析，或正确配置支持 UDP/TCP 四层转发的产品。

Compose 会放行 ZLMediaKit 内部的播放和发布 hook，供已经通过 EasyMedia 网关鉴权的 WHIP/WHEP 请求完成协商。EasyMedia 使用内嵌 ZLM C API 交换 SDP，并在网关层提供 OBS 所需的标准 `Location` 和会话 `DELETE`。ZLMediaKit 的 HTTP、RTSP listener 保持关闭，RTMP listener 只在 `1935/tcp` 接收设备推流。

同一份域名证书还会通过 Docker secret 提供给 EasyMedia，入口脚本将证书链和私钥合并为仅容器用户可读的 PEM，用于 WebRTC DTLS。更新证书后需要重建 EasyMedia 容器，而不只是重载 Nginx。

## 4. 构建并启动

首次构建会下载 Maven 依赖并从固定提交构建 ZLMediaKit，耗时会比普通 Java 镜像长：

```bash
docker compose up -d --build
```

查看状态和日志：

```bash
docker compose ps
docker compose logs -f easymedia nginx
```

停止服务：

```bash
docker compose down
```

运行数据保存在 Docker volume `simple-secret-easymedia_easymedia-data` 中。需要同时删除运行数据时才使用：

```bash
docker compose down -v
```

## 5. HTTPS WebRTC 地址

WHIP 推流地址：

```text
https://webrtc.junpzx.cn/easyMedia/api/webrtc/whip?app=live&stream=camera-01
```

WHEP 播放地址：

```text
https://webrtc.junpzx.cn/easyMedia/api/webrtc/whep?app=live&stream=camera-01
```

把示例域名、`app` 和 `stream` 替换为实际值。请求必须是 WebRTC 客户端生成的 SDP Offer，方法为 `POST`，请求和响应类型均为 `application/sdp`。成功状态码为 `201`，并包含 `/easyMedia/api/webrtc/sessions/{sessionId}` 格式的 `Location` 响应头。

仅验证 HTTPS 和路由是否可达，可以执行：

```bash
curl -i https://webrtc.junpzx.cn/easyMedia/api/webrtc/whip
```

这里没有提交 SDP，收到 `400` 或 `405` 说明 HTTPS、Nginx 和 Java 应用链路已经可达；这不代表媒体协商已经完成。

## 6. SSE 订阅设备 SEI 数据

订阅指定设备的完整 SEI 解析结果：

```bash
curl -N -H 'Accept: text/event-stream' \
  'https://webrtc.junpzx.cn/easyMedia/api/sei/devices/device-01/events'
```

连接建立后先收到 `connected` 事件；解析到完整 SEI 后收到 `sei` 事件，每 15 秒收到一次
`heartbeat`。`data` 是可解码时的 UTF-8 内容；`payloadBase64` 始终保留完整原始 payload。
对于 `payloadType=5`，`data` 已跳过开头 16 字节 UUID，完整内容仍保留在 `payloadBase64`。
部署目录中的播放器使用兼容诊断接口
`/easyMedia/api/sei/events?app=live&stream=device-01`，该接口也已关闭 Nginx 响应缓冲。

## 7. 更新证书

替换以下两个文件后重载 Nginx：

```text
docker/certs/fullchain.pem
docker/certs/privkey.pem
```

```bash
docker compose exec nginx nginx -s reload
docker compose up -d --force-recreate easymedia
```

## 8. 常见问题

- HTTPS 正常但 WebRTC 无画面：确认 `8000/udp` 和 `8000/tcp` 已双向放行，并检查 `.env` 中的 `WEBRTC_PUBLIC_IP` 是否为浏览器可访问的公网 IPv4。
- SSE 有心跳但没有 `sei`：确认设备推流地址的 app 为 `live`、stream 与订阅的 `deviceId` 完全一致，并检查 `1935/tcp` 安全组规则。
- Nginx 启动失败：检查证书文件名、PEM 格式、证书链完整性，以及私钥是否和证书匹配。
- 域名解析失败：先执行 `getent ahostsv4 <域名>`；必要时直接设置 `WEBRTC_PUBLIC_IP`。
- 修改 `.env` 后未生效：执行 `docker compose up -d --force-recreate`。
- 生产接入：本模块固定使用测试身份，适合联调验证，不应直接替代业务系统的认证、授权和限流配置。

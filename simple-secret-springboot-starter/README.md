# Simple Secret Spring Boot Starters

该目录聚合当前保留的 Spring Boot 3.5 starter。聚合 POM 不应作为运行时依赖，应用应按功能选择具体模块。

## 模块选择

- `mqttv3`：MQTT 3.1.1 多客户端、订阅、发布和请求响应。
- `mqttv5`：MQTT v5 多客户端、共享订阅、发布和请求响应。
- `camera`：摄像机与 NVR RTSP 地址组装。
- `nats`：NATS 发布、请求响应和 queue group 订阅。
- `influxdb`：InfluxDB 1.x 映射、查询和初始化。
- `netty-websocket`：独立 Netty WebSocket 服务端。
- `zlm4j`：嵌入式 ZLMediaKit 管理能力。
- `easymedia`：基于 zlm4j 的 WebRTC 网关和媒体管理能力。

## 自动配置流程

```mermaid
flowchart LR
    CONFIG["simple-secret.* 配置"] --> CONDITION["开关与 classpath 条件"]
    CONDITION --> VALIDATE["参数和安全边界校验"]
    VALIDATE --> BEAN["创建可覆盖 Bean"]
    BEAN --> RESOURCE["连接、线程或原生资源"]
    RESOURCE --> STOP["Spring 生命周期关闭"]
```

所有会连接外部服务、监听端口或启动原生资源的能力都要求显式启用。凭据、令牌和设备密码必须从环境变量
或安全配置系统注入。业务应用可通过声明同类型 Bean 覆盖默认实现，具体覆盖点见各 starter README。

## 依赖边界

starter 只传递运行功能必需的依赖。MQTT v3/v5 直接依赖 Jackson 处理消息 payload，不依赖已删除的 JSON
starter。Netty WebSocket 不依赖 Servlet WebSocket。各 starter 的配置前缀、Bean 名和生命周期彼此隔离。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -pl simple-secret-springboot-starter -am verify
```

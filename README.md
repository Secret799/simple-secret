# Simple Secret

Simple Secret 是一组按需引入的 Java 17 插件和 Spring Boot 3.5 starter。项目遵循最小依赖原则：应用只需要引入实际使用的模块，不需要依赖整个项目。

## 模块说明

- `simple-secret-application`：应用聚合模块，当前包含可运行的 EasyMedia 测试应用。
- `simple-secret-common`：内部基础公共构件和对外 BOM。
- `simple-secret-plugins`：纯 Java 插件聚合模块，当前包含 Geo Referencing 和 KMZ/KML。
- `simple-secret-plugin-geo`：零运行时依赖的像素坐标、WGS84 地理坐标和 DJI 照片/遥测投影。
- `simple-secret-plugin-kmz`：零运行时依赖的 KML、KMZ、DJI WPML 航点任务和 LineString 读取。
- `simple-secret-springboot-starter-json`：JSON 编解码、属性名解析和 Spring Boot 自动配置。
- `simple-secret-springboot-starter-mqttv5`：MQTT v5 多客户端、发布订阅和请求响应。
- `simple-secret-springboot-starter-nats`：NATS 多客户端、发布、请求响应和 queue group 订阅。
- `simple-secret-springboot-starter-influxdb`：InfluxDB 1.x 注解映射、安全 InfluxQL DSL、写入、查询和初始化。
- `simple-secret-springboot-starter-zlm4j`：嵌入式 ZLMediaKit、媒体代理、录像、RTP、截图和转码。
- `simple-secret-springboot-starter-easymedia`：基于 zlm4j 的 WebRTC 网关、媒体管理 API、H.264 裸流和 UDP 组播能力。

## 接入准备

推荐先导入 BOM，再按需声明插件或 starter。以下教程均使用 `1.1.0`：

```xml
<repositories>
    <repository>
        <id>junpzx-custom-nexus</id>
        <url>https://repository.junpzx.cn/repository/maven-group/</url>
    </repository>
</repositories>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.ss</groupId>
            <artifactId>simple-secret-common-bom</artifactId>
            <version>1.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

所有模块的 Java 包统一使用 `com.ss.<模块缩写>.*`。自动配置均支持按模块关闭，不使用的模块不会主动启动外部连接或原生服务。

## Geo Referencing Plugin

`simple-secret-plugin-geo` 提供通用像素/地理坐标转换、检测框批量定位、管线投影、DJI 照片 EXIF/XMP 读取和实时遥测投影。模块没有生产依赖，也不需要 Spring 容器：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-geo</artifactId>
</dependency>
```

```java
CameraState camera = new CameraState()
        .setLat(31.2304).setLon(121.4737).setAlt(120)
        .setGimbalYaw(30).setGimbalPitch(-90).setGimbalRoll(0)
        .setFovH(84).setFovV(53)
        .setFrameWidth(4000).setFrameHeight(3000);

GeoTarget target = GeoReferencer.pixelToGeo(
        new PixelCoordinate(2000, 1500), camera, 5.0);
```

完整的 DJI 照片、实时遥测、DEM 和管线投影案例见 [Geo Plugin README](simple-secret-plugins/simple-secret-plugin-geo/README.md)。

## KMZ/KML Plugin

`simple-secret-plugin-kmz` 提供 KML/WPML 航点任务读写、安全 KMZ 解压打包和普通 KML `LineString` 读取：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-kmz</artifactId>
</dependency>
```

```java
KmzMission mission = KmzMission.builder()
        .missionName("inspection")
        .waypoints(List.of(new Waypoint()
                .setIndex(0)
                .setCoordinate(new Coordinate(116.3975, 39.9087, 80))
                .setExecuteHeight(120)
                .setWaypointSpeed(8)))
        .build();

KmzWriter.write(mission, Path.of("inspection.kmz"));
KmzMission parsed = KmzParser.parse(Path.of("inspection.kmz"));
```

模块不会关闭调用方传入的流，并限制 XML、压缩输入、解压条目、航点和动作数量。完整的 DJI 文件选择规则、LineString 与 Geo 映射案例见 [KMZ Plugin README](simple-secret-plugins/simple-secret-plugin-kmz/README.md)。

## JSON Starter

### 功能和依赖边界

`simple-secret-springboot-starter-json` 提供：

- 非 Spring 场景可直接使用的 `JsonUtils`。
- 可绑定调用方 `ObjectMapper` 的 `JsonCodec`。
- JavaScript 安全整数和 `BigDecimal` 精度保护。
- getter 方法引用到 JSON 属性名的解析。
- 判断对象实例字段是否全部为空的 Jackson 值过滤器。
- 可选的 Spring Boot Jackson 定制。

核心依赖只有 Jackson 和内部 toolbox。`spring-boot-autoconfigure`、`spring-web` 均为 optional，因此普通 Java 项目使用静态工具时不会被强制传递 Spring Web。

### Maven 依赖

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-json</artifactId>
</dependency>
```

### 非 Spring 使用

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.ss.json.utils.JsonUtils;

import java.util.List;
import java.util.Map;

String json = JsonUtils.toJsonString(new User(1L, "Alice"));
User user = JsonUtils.parseObject(json, User.class);

List<User> users = JsonUtils.parseArray(
        "[{\"id\":1,\"name\":\"Alice\"}]", User.class);
Map<String, Object> attributes = JsonUtils.parseMap("{\"enabled\":true}");
List<User> genericUsers = JsonUtils.parseObject(
        "[{\"id\":1,\"name\":\"Alice\"}]",
        new TypeReference<List<User>>() { });
```

`JsonUtils` 始终使用自身独立的默认 `ObjectMapper`，不会读取或修改 Spring 容器中的 Jackson 配置。空字符串解析对象时返回 `null`，空字符串解析数组时返回不可变空列表；解析失败统一抛出 `JsonOperationException`，异常消息不会回显原始 JSON 正文。

### Spring Boot 使用

自动配置默认开启。业务代码优先注入 `JsonCodec`，它会使用宿主应用已有的 `ObjectMapper`；如果应用上下文中没有 mapper，则使用 starter 的独立默认 mapper：

```java
import com.ss.json.JsonCodec;
import org.springframework.stereotype.Service;

@Service
public class AuditPayloadService {
    private final JsonCodec jsonCodec;

    public AuditPayloadService(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public String encode(AuditEvent event) {
        return jsonCodec.toJsonString(event);
    }

    public AuditEvent decode(String payload) {
        return jsonCodec.parseObject(payload, AuditEvent.class);
    }
}
```

starter 默认不会修改宿主应用的 `ObjectMapper`。需要把安全整数、`BigDecimal` 字符串格式、时区和序列化特性应用到宿主 mapper 时显式开启：

```yaml
simple-secret:
  json:
    enabled: true
    jackson-customization-enabled: true
```

宿主应用注册的同类型 Jackson 模块优先级更高。修改宿主 mapper 还要求 classpath 中存在 `Jackson2ObjectMapperBuilder`，通常由 `spring-boot-starter-web` 提供。非 Web Boot 环境没有 `ObjectMapper` Bean 时，starter 只发布 `JsonCodec`，不会额外发布 `ObjectMapper`。

### 属性名和空对象过滤案例

```java
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ss.json.filter.EmptyObjectFilter;
import com.ss.json.property.JsonPropertyNameResolver;
import com.ss.json.property.NameCase;

class User {
    @JsonProperty("display_name")
    private String userName;

    @JsonInclude(value = JsonInclude.Include.CUSTOM,
            valueFilter = EmptyObjectFilter.class)
    private Profile profile;

    public String getUserName() {
        return userName;
    }
}

String property = JsonPropertyNameResolver.resolve(
        User::getUserName, "-", NameCase.LOWER);
// 字段存在 @JsonProperty，因此结果是 display_name。
```

### 数值精度规则

默认 mapper 会将 JavaScript 安全整数范围 `[-9007199254740991, 9007199254740991]` 外的 `Long`、`BigInteger` 序列化为 JSON 字符串，并将 `BigDecimal` 序列化为字符串：

```json
{"safe":9007199254740991,"unsafe":"9007199254740992","amount":"12.3400"}
```

如果上下游协议要求所有数字必须使用 JSON number，应保持 `jackson-customization-enabled=false` 并使用宿主自己的 mapper 配置。

## MQTT v5 Starter

### 功能和依赖边界

`simple-secret-springboot-starter-mqttv5` 提供多客户端连接、发布订阅、共享订阅、同步请求与重试、断线重连、Spring Bean 处理器发现和配置刷新。它传递依赖 JSON starter，因此消息上下文可以直接把 JSON payload 转为对象。

### Maven 依赖

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-mqttv5</artifactId>
</dependency>
```

### 最小配置

只有 `clients` 中显式设置 `enabled: true` 的客户端会建立连接。starter 不提供默认 broker、用户名或密码：

```yaml
simple-secret:
  mqtt:
    enabled: true
    clients:
      default:
        enabled: true
        broker: tcp://localhost:1883
        client-id: simple-secret-demo
        clean-start: true
        keep-alive-seconds: 30
        connection-timeout-seconds: 10
        publish-timeout-seconds: 10
        reconnect-enabled: true
        reconnect-delay-millis: 1000
```

生产环境建议通过密钥系统或环境变量注入账号密码。需要持久会话时应配置稳定且唯一的 `client-id`，并按 broker 语义设置 `clean-start: false`。

### 接收消息

将 `MqttMessageHandler` 实现声明为 Spring Bean，starter 会在客户端每次连接或重连成功后恢复订阅：

```java
import com.ss.mqttv5.handler.MqttMessageHandler;
import com.ss.mqttv5.message.MqttMessageContext;
import org.springframework.stereotype.Component;

@Component
public class DeviceStateHandler implements MqttMessageHandler {
    @Override
    public String topic() {
        return "devices/+/state";
    }

    @Override
    public int qos() {
        return 1;
    }

    @Override
    public void handle(MqttMessageContext message) {
        DeviceState state = message.getPayload(DeviceState.class);
        String actualTopic = message.getTopic();
        // 持久化状态；异常由业务侧自行记录或转换。
    }
}
```

处理器还可以通过 `clientKey()` 选择非默认客户端，通过 `shareGroup()` 创建 MQTT v5 共享订阅：

```java
@Component
public class SharedTelemetryHandler implements MqttMessageHandler {
    @Override
    public String clientKey() {
        return "telemetry";
    }

    @Override
    public String topic() {
        return "devices/+/telemetry";
    }

    @Override
    public String shareGroup() {
        return "telemetry-workers";
    }

    @Override
    public void handle(MqttMessageContext message) {
        String payload = message.getPayloadAsString();
    }
}
```

`MqttMessageContext` 是消息快照，支持 `getPayloadAsString()`、`getPayload(Class<T>)`、`getPayload(TypeReference<T>)`，并可读取实际 topic、订阅 filter、client key、QoS、retained 标记和 MQTT v5 properties。

### 发布消息

```java
import com.ss.json.JsonCodec;
import com.ss.mqttv5.client.MqttClientManager;
import org.springframework.stereotype.Service;

@Service
public class DeviceCommandPublisher {
    private final MqttClientManager mqtt;
    private final JsonCodec jsonCodec;

    public DeviceCommandPublisher(MqttClientManager mqtt, JsonCodec jsonCodec) {
        this.mqtt = mqtt;
        this.jsonCodec = jsonCodec;
    }

    public void reboot(String deviceId) {
        String payload = jsonCodec.toJsonString(
                new DeviceCommand("reboot", System.currentTimeMillis()));
        mqtt.publish("default", "devices/" + deviceId + "/commands", payload, 1);
    }
}
```

发布前可使用 `mqtt.isConnected("default")` 判断连接状态。topic、filter、QoS 或连接状态不合法时会快速失败；发布异常统一包装为 `MqttOperationException`。

### 请求响应和重试案例

请求正文与响应正文必须能提取相同的关联键：

```java
import com.ss.json.utils.JsonUtils;
import com.ss.mqttv5.message.MqttMessageContext;
import com.ss.mqttv5.waiter.MqttCorrelationExtractor;

import java.time.Duration;
import java.util.Optional;

String requestJson = "{\"requestId\":\"req-1001\",\"action\":\"status\"}";
MqttCorrelationExtractor extractor = (type, payload) ->
        String.valueOf(JsonUtils.parseMap(payload).get("requestId"));

Optional<MqttMessageContext> response = mqtt.requestWithRetry(
        "default",
        "devices/device-01/requests",
        "devices/device-01/replies",
        requestJson,
        1,
        Duration.ofSeconds(3),
        extractor,
        3);

DeviceReply reply = response
        .map(message -> message.getPayload(DeviceReply.class))
        .orElseThrow(() -> new IllegalStateException("MQTT request timed out"));
```

`attempts` 表示总发布次数而不是额外重试次数。同步等待会临时订阅 reply filter，并在完成、超时或异常后释放；业务关联键必须唯一，避免并发请求互相覆盖。

### 非 Spring 使用

```java
import com.ss.mqttv5.client.MqttClientManager;
import com.ss.mqttv5.config.MqttClientOptions;
import com.ss.mqttv5.waiter.DefaultMqttResponseWaiter;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

ExecutorService publisher = Executors.newFixedThreadPool(2);
ExecutorService handlers = Executors.newFixedThreadPool(4);
ScheduledExecutorService connections = Executors.newScheduledThreadPool(1);

MqttClientManager manager = new MqttClientManager(
        publisher, handlers, connections, new DefaultMqttResponseWaiter());
MqttClientOptions options = new MqttClientOptions();
options.setEnabled(true);
options.setBroker("tcp://localhost:1883");
options.setClientId("standalone-client");

CountDownLatch connected = new CountDownLatch(1);
manager.refreshClients(
        Map.of("default", options),
        (clientKey, current) -> connected.countDown());
if (!connected.await(10, TimeUnit.SECONDS)) {
    throw new IllegalStateException("MQTT connection timed out");
}
manager.publish("default", "demo/events", "{\"ok\":true}", 1);

manager.close();
publisher.shutdown();
handlers.shutdown();
connections.shutdown();
```

`MqttClientManager.close()` 只关闭 MQTT 客户端，不关闭调用方传入的线程池。自动配置创建的线程池由 Spring 负责销毁。

唯一配置前缀是 `simple-secret.mqtt`，设置 `simple-secret.mqtt.enabled=false` 可完全关闭自动配置。若运行环境存在 Spring Cloud，starter 会通过反射识别 `EnvironmentChangeEvent`，只有该前缀下的键发生变化时才刷新客户端，starter 本身不直接依赖 Spring Cloud。

## NATS Starter

`simple-secret-springboot-starter-nats` 提供 NATS 多客户端、发布、请求响应、普通订阅、queue group 和 Spring Bean 自动订阅。模块不依赖 JSON starter，应用可自行选择 payload codec：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-nats</artifactId>
</dependency>
```

只有显式启用的客户端会连接，starter 没有默认服务器或凭据：

```yaml
simple-secret:
  nats:
    clients:
      edge:
        enabled: true
        url: nats://localhost:4222
        connection-name: edge-service
```

声明消息处理器：

```java
@Component
public class DeviceStateHandler implements NatsMessageHandler {
    @Override
    public String clientKey() {
        return "edge";
    }

    @Override
    public String subject() {
        return "devices.*.state";
    }

    @Override
    public void handle(NatsMessageContext message) {
        String payload = message.getPayloadAsString();
    }
}
```

发布与请求响应：

```java
natsClientManager.publish("edge", "devices.a.commands", "restart");

Optional<NatsMessageContext> response = natsClientManager.request(
        "edge", "devices.a.status.request", new byte[0], Duration.ofSeconds(2));
```

`queue()` 默认为空，只有业务显式返回 queue group 才启用负载均衡。发布会等待 flush；请求超时返回空值；错误统一抛出 `NatsOperationException`，日志不输出 payload 或凭据。完整的校验器、JSON 解码、非 Spring 和安全案例见 [NATS Starter README](simple-secret-springboot-starter/simple-secret-springboot-starter-nats/README.md)。

## InfluxDB Starter

`simple-secret-springboot-starter-influxdb` 提供 InfluxDB 1.x 注解实体映射、单条和批量写入、安全 Lambda 查询、结果映射、分页、数据库与 retention policy 管理。默认关闭，不提供连接地址、凭据、数据库或自动初始化默认值：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-influxdb</artifactId>
</dependency>
```

```yaml
simple-secret:
  influxdb:
    enabled: true
    url: http://localhost:8086
    database:
      name: metrics
    username: ${INFLUXDB_USERNAME:}
    password: ${INFLUXDB_PASSWORD:}
```

```java
@Measurement(name = "telemetry")
public class Telemetry {
    @Column(name = "device_id", tag = true)
    private String deviceId;

    @Column
    private Double value;

    public Telemetry() {
    }

    public Telemetry(String deviceId, Double value) {
        this.deviceId = deviceId;
        this.value = value;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Double getValue() {
        return value;
    }
}

operations.save(new Telemetry("device-a", 12.5));

List<Telemetry> records = operations.list(
        operations.wrapper(Telemetry.class)
                .eq(Telemetry::getDeviceId, "device-a")
                .ge(Telemetry::getValue, 10)
                .orderByTimeDesc()
                .limit(100));
```

DSL 对 identifier、函数、duration、数值类型和字符串字面量统一校验与转义，不提供任意原始条件片段。分页计数必须选择非 tag、非 time 的普通 field，并且分页查询不能包含分组。原始 InfluxQL API 只适合可信代码，禁止拼接请求参数。完整的实体、分页、Service、自动初始化、非 Spring 和安全案例见 [InfluxDB Starter README](simple-secret-springboot-starter/simple-secret-springboot-starter-influxdb/README.md)。

## ZLM4J Starter

### 功能和运行边界

`simple-secret-springboot-starter-zlm4j` 通过 `com.aizuda:zlm4j` 的 JNA 绑定在当前 JVM 中运行 ZLMediaKit，提供推拉流代理、流查询、MP4/TS 录像、RTP 服务、统计、服务器配置、FFmpeg 转码和截图。该 starter 面向 Spring Boot 使用，只有显式开启时才初始化原生媒体服务。

### Maven 依赖

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-zlm4j</artifactId>
</dependency>
```

### 最小配置

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

ZLM 启动时以原生 `mk_ini_default()` 为基础，再应用 `ZlmMediaProperties` 明确暴露的配置字段。starter 内置的 `simple-secret__zlm4j-default__conf.ini` 用于内部默认配置加载，不是任意 INI 键的透传入口；第三方应用应只使用配置元数据中存在的 `simple-secret.zlm4j.*` 属性。启用前应确认配置端口未被占用，并由部署环境提供与操作系统、CPU 架构匹配的 ZLMediaKit 原生库 `mk_api`。

截图、转码和视频拼接还依赖 JavaCPP FFmpeg native。starter 项目中的 Maven OS profile 只在构建 starter 源码时生效，不会传递给消费项目，因此宿主必须按部署平台显式增加 runtime classifier。Linux x86-64 示例：

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

其他受支持 classifier 为 `linux-arm64`、`windows-x86_64`、`macosx-arm64`、`macosx-x86_64`，FFmpeg classifier 均需追加 `-gpl`。只使用不经过 FFmpeg 的媒体服务接口时无需额外引入这些 classifier，但 `mk_api` 仍必须由部署环境提供。

### 拉流代理和在线状态案例

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

`addStreamPullerProxy` 返回的代理 key 应由业务侧保存，用于精确删除代理。不要把不可信客户端提交的 URL 直接传入服务，仍应在业务层校验租户、设备归属和允许的媒体源。

### 录像、RTP、截图和转码

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

### 监听 ZLM 事件

ZLM hook 会转换为 Spring 事件，可以按业务需要监听：

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

### 资源访问和部署安全

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

`StartRecordBO.customizedPath` 非空时只能写入 `recording-root` 下；为空时 ZLM 使用 `mp4-save-path` 或 `hls-save-path`，这两个全局保存路径不会再经过 `recording-root` 校验，生产配置也必须把它们限制在专用媒体目录。生产环境还应在网络层限制 RTSP、RTMP、HTTP 和 RTC 端口，并在业务层实现播放、推流鉴权。

FFmpeg 使用 `*-gpl` classifier，发布或分发应用前必须自行确认 GPL 许可义务。

## EasyMedia Starter

### 功能和依赖边界

`simple-secret-springboot-starter-easymedia` 建立在 zlm4j starter 之上，提供 WHIP/WHEP WebRTC 网关、会话仓储、鉴权限流、失败关闭补偿、通用媒体管理 API、H.264 裸流推送和 UDP 组播监听。

该 starter 只在以下条件同时满足时加载：

- `simple-secret.zlm4j.enabled=true`。
- `simple-secret.easymedia.enabled=true`。
- classpath 存在 Servlet、Jakarta Validation 和 Spring Web MVC。

### Maven 依赖

EasyMedia 会传递依赖 zlm4j。宿主 Web 应用还需要显式提供 Web MVC 和 Validation：

```xml
<dependencies>
    <dependency>
        <groupId>com.ss</groupId>
        <artifactId>simple-secret-springboot-starter-easymedia</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

### 最小安全配置

```yaml
simple-secret:
  zlm4j:
    enabled: true
  easymedia:
    enabled: true
    management-api-enabled: false
    webrtc:
      enabled: true
      local-zlm-enabled: true
      max-sdp-bytes: 65536
      security:
        authentication-required: true
      rate-limit:
        enabled: true
        publish-per-minute: 60
        play-per-minute: 120
        session-operation-per-minute: 300
```

推荐的内嵌模式使用 `local-zlm-enabled=true`，网关直接调用当前 JVM 的 ZLM C API，不需要放宽 ZLM 的匿名播放和推流 hook。该模式只完成 SDP Offer/Answer，不提供可 PATCH/DELETE 的上游会话资源。当前内嵌 ZLMediaKit 不支持 Trickle ICE PATCH，因此 `trickle-ice-enabled` 默认关闭。

### WHIP 推流和 WHEP 播放

以下请求假定宿主应用已经按“接入宿主认证和授权”一节提供身份解析，并能从 Bearer Token 建立当前用户上下文。保持默认安全配置但未提供 `WebRtcIdentityProvider` 时，请求会返回 `401`。

创建 WHIP 会话：

```bash
curl -i \
  -X POST \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/sdp' \
  --data-binary @offer.sdp \
  'http://localhost:8080/easyMedia/api/webrtc/whip?app=live&stream=camera-01'
```

创建 WHEP 会话：

```bash
curl -i \
  -X POST \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/sdp' \
  --data-binary @offer.sdp \
  'http://localhost:8080/easyMedia/api/webrtc/whep?app=live&stream=camera-01'
```

响应 body 是 `application/sdp` 的 SDP Answer。推荐的本地 C API 模式不会返回可管理的 `Location`，媒体连接由 WebRTC 客户端自身结束。

如果确实需要 `Location`、PATCH、DELETE 和失败关闭补偿，可改用受管 HTTP 信令模式：

```yaml
simple-secret:
  zlm4j:
    enabled: true
    listen-ip: 127.0.0.1
    allow-anonymous-play: true
    allow-anonymous-publish: true
  easymedia:
    enabled: true
    webrtc:
      local-zlm-enabled: false
      signaling-base-url: http://127.0.0.1:7080
```

HTTP 转发器不会把外部 Bearer Token 或 `WebRtcIdentity` 传给上游 ZLM，因此上例必须允许上游匿名 hook。该配置只适用于 ZLM 全部媒体端口均绑定回环地址或处于严格隔离的可信内部网络；不得在放宽匿名 hook 后把 ZLM RTSP、RTMP、HTTP、RTC 端口直接暴露到公网。公网用户仍必须先通过 EasyMedia 网关的宿主认证和授权。

受管 HTTP 模式会通过 `Location` 返回会话资源，关闭时使用该地址：

```bash
curl -i -X DELETE \
  -H 'Authorization: Bearer <access-token>' \
  'http://localhost:8080/easyMedia/api/webrtc/sessions/{sessionId}'
```

上游明确支持 Trickle ICE 且配置 `trickle-ice-enabled=true` 后，受管 HTTP 模式才可以发送：

```bash
curl -i \
  -X PATCH \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/trickle-ice-sdpfrag' \
  --data-binary @candidate.sdpfrag \
  'http://localhost:8080/easyMedia/api/webrtc/sessions/{sessionId}'
```

### 接入宿主认证和授权

默认 `authentication-required=true` 且没有固定 `default-subject`，匿名请求会被拒绝。生产环境应提供自己的 `WebRtcIdentityProvider`，并按需替换 `WebRtcAccessPolicy`：

```java
import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.security.WebRtcIdentityProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WebRtcSecurityConfiguration {
    @Bean
    WebRtcIdentityProvider webRtcIdentityProvider(CurrentUserService currentUser) {
        return clientIp -> {
            CurrentUser user = currentUser.requiredUser();
            return new WebRtcIdentity(
                    user.tenantId(), user.userId(), true);
        };
    }
}
```

默认访问策略会校验认证状态和会话所有权。多租户应用如果允许管理员跨主体操作会话，应实现 `WebRtcAccessPolicy`，同时保留租户隔离和显式权限判断。

### 开启媒体管理 API

`/easyMedia/api/common/*` 默认不暴露。即使配置 `management-api-enabled=true`，starter 也会默认拒绝全部管理请求；宿主必须提供授权 Bean：

```java
import com.ss.easymedia.security.EasyMediaManagementAuthorizer;
import org.springframework.context.annotation.Bean;

@Bean
EasyMediaManagementAuthorizer easyMediaManagementAuthorizer() {
    return request -> request.isUserInRole("MEDIA_ADMIN");
}
```

授权器只保护通用管理路径，不能替代 WebRTC 的 `WebRtcIdentityProvider` 和 `WebRtcAccessPolicy`。不需要 HTTP 管理能力时应保持 `management-api-enabled=false`，直接注入 zlm4j 服务接口完成业务操作。

### H.264 裸流推送

`H264NakedFlowPushZlmManager` 不是默认 Bean。需要时由宿主显式声明，以保持非必要能力不初始化：

```java
import com.ss.easymedia.h264.H264NakedFlowPushZlmManager;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import org.springframework.context.annotation.Bean;

@Bean(destroyMethod = "close")
H264NakedFlowPushZlmManager h264PushManager(
        ZlmMediaProperties properties) {
    return new H264NakedFlowPushZlmManager(properties);
}
```

业务侧按 H.264 Annex-B 数据到达顺序推送，并在结束时释放单路流：

```java
h264PushManager.push("live", "camera-01", h264Bytes);
h264PushManager.stopPush("live", "camera-01");
```

默认处理队列容量为 150。使用三参数构造器时，`processQueueSize` 必须大于 0，不再支持无界队列。队列已满时，`push` 会阻塞形成背压；等待期间线程被中断会抛出 `InterruptedException`，对应流被停止时会快速抛出 `IllegalStateException`，不会永久阻塞在已无人消费的队列上。

单个尚未完成重组的 NALU 最多累积 16 MiB。超过上限时读取器会丢弃该不完整 NALU、记录异常并继续处理后续片段，避免异常输入持续占用堆内存。长时间没有数据的流会自动回收；应用关闭时 Spring 会调用 `close()` 释放全部原生资源。

### UDP 组播

启用 EasyMedia 后会自动提供 `UdpMulticastManager`：

```java
import com.ss.easymedia.support.udp.UdpMulticastManager;

udpMulticastManager.joinGroup(
        "239.10.10.10",
        5000,
        "192.168.1.20",
        packet -> process(packet.getData(), packet.getOffset(), packet.getLength()));

udpMulticastManager.leaveGroup(
        "239.10.10.10", 5000, "192.168.1.20");
```

`groupIp` 和 `localIp` 必须使用数字 IP，且地址族一致；`groupIp` 必须是组播地址，端口范围为 1 到 65535，`localIp` 必须绑定在部署主机的单播网卡上，handler 不能为空。`setMaxMessageLength` 只允许在线程启动前调用，报文长度范围为 1 到 65507 字节。重复加入相同的本地 IP、组播地址和端口不会创建第二个监听器；Spring 容器关闭时会停止全部监听器。

### 单机和多实例部署

Redisson 是 optional 依赖。classpath 中存在 `RedissonClient` Bean 时，WebRTC 自动使用 Redis 会话仓储和分布式限流；否则使用单机内存仓储和有界本地限流。多实例生产部署应由宿主显式引入并配置 Redisson，所有实例还应使用相同的会话 TTL、限流配置和 ZLM 路由策略。

WebRTC 入口应置于 HTTPS 反向代理之后，并由代理限制请求体、连接数和可信转发头。不要在公网直接暴露 ZLM 原生管理端口或 EasyMedia 通用管理接口。

### 可运行测试应用

[`simple-secret-application-easymedia-test`](simple-secret-application/simple-secret-application-easymedia-test/README.md) 提供了一个参考 Honeybee application 组织方式的最小 Spring Boot 测试程序。它直接复用 EasyMedia starter 的 WebRTC 和媒体管理控制器，默认绑定 `127.0.0.1:9878`，并通过 `local` profile 启用内嵌 ZLM。

构建并启动：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
mvn -pl simple-secret-application/simple-secret-application-easymedia-test \
    -am package

JAVA_HOME=/opt/homebrew/opt/openjdk@17 \
DYLD_LIBRARY_PATH=/path/to/zlmediakit/lib \
java -Djna.library.path=/path/to/zlmediakit/lib \
  -jar simple-secret-application/simple-secret-application-easymedia-test/target/simple-secret-application-easymedia-test.jar \
  --spring.profiles.active=local
```

运行环境必须提供与当前平台匹配的 ZLMediaKit `mk_api` 动态库。`jna.library.path` 负责定位直接加载的 `mk_api`，其传递动态库依赖仍由系统链接器通过 macOS `DYLD_LIBRARY_PATH`、Linux `LD_LIBRARY_PATH`、Windows `PATH`、rpath 或系统安装目录解析。完整的管理令牌、WHIP/WHEP 请求和真实合约测试示例见该模块 README。

## 构建验证

项目使用 Java 17。离线依赖已具备时可执行：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -o clean verify
```

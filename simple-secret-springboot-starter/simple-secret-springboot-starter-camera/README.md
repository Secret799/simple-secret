# Simple Secret Camera Starter

`simple-secret-springboot-starter-camera` 为 Java 17 和 Spring Boot 3.5 应用提供摄像机 RTSP 地址组装，内置支持海康威视和大华的独立摄像机、NVR。

该模块只负责根据显式参数生成 URL，不探测设备、不发起网络连接、不加载厂商原生库。运行时仅依赖 Spring Boot 自动配置和 Spring Context，不依赖 Honeybee、Hutool、Lombok、JSON、JNA、厂商 SDK 或 ZLM4J。

## Maven 依赖

推荐导入 Simple Secret BOM 后按需声明：

```xml
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

<dependencies>
    <dependency>
        <groupId>com.ss</groupId>
        <artifactId>simple-secret-springboot-starter-camera</artifactId>
    </dependency>
</dependencies>
```

未使用 BOM 时显式指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-camera</artifactId>
    <version>1.1.0</version>
</dependency>
```

## 配置

自动配置默认启用，不需要设备连接配置。需要完全关闭 Bean 注册时设置：

```yaml
simple-secret:
  camera:
    enabled: false
```

启用时会注册四个内置 `UrlAssemblyService` 和一个不可变、线程安全的 `UrlAssemblyHolder`：

```java
import com.ss.camera.domain.StreamUrlAssemblyDomain;
import com.ss.camera.enums.CameraBrandEnums;
import com.ss.camera.enums.CameraTypeEnums;
import com.ss.camera.service.UrlAssemblyHolder;
import org.springframework.stereotype.Service;

@Service
public class CameraAddressService {
    private final UrlAssemblyHolder holder;

    public CameraAddressService(UrlAssemblyHolder holder) {
        this.holder = holder;
    }

    public String mainStream() {
        StreamUrlAssemblyDomain request = new StreamUrlAssemblyDomain()
                .setBrand(CameraBrandEnums.HIKVISION.getCode())
                .setType(CameraTypeEnums.NVR.getCode())
                .setIp("192.0.2.10")
                .setPort("554")
                .setAccount("admin")
                .setPassword(System.getenv("CAMERA_PASSWORD"))
                .setChannelNo("1")
                .setStreamType("main");
        return holder.assembly(request);
    }
}
```

账号和密码会作为 URI user-info 分别执行 UTF-8 百分号编码。例如密码 `p@ss` 会生成 `p%40ss`。返回值仍包含可用凭据，禁止把完整 URL 写入日志、异常详情、链路标签或返回给无权访问设备的调用方。

## 内置地址规则

| 品牌 | 类型 | `streamType` | 地址路径规则 |
| --- | --- | --- | --- |
| `Hikvision` | `CAMERA` | `main`、`sub` 等安全标识 | `/h264/ch{channelNo}/{streamType}/av_stream` |
| `Hikvision` | `NVR` | `main` 使用 `01`，其他使用 `02` | `/Streaming/Channels/{channel}01` 或 `02` |
| `Dahua` | `CAMERA` | `main` 使用 `0`，其他使用 `1` | `/cam/realmonitor?channel={channelNo}&subtype={subtype}` |
| `Dahua` | `NVR` | `main` 使用 `0`，其他使用 `1` | `/cam/realmonitor?channel={channelNo}&subtype={subtype}` |

海康 NVR 同时接受基础通道号 `1` 和已经带码流后缀的 `101`、`102`。组装时会根据 `streamType` 统一规范为 `101` 或 `102`，避免重复追加后缀。

示例：

```java
StreamUrlAssemblyDomain dahuaSubStream = new StreamUrlAssemblyDomain()
        .setBrand("Dahua")
        .setType("CAMERA")
        .setIp("camera.example.com")
        .setPort("554")
        .setAccount("operator")
        .setPassword("secret")
        .setChannelNo("2")
        .setStreamType("sub");

String url = holder.assembly(dahuaSubStream);
// rtsp://operator:secret@camera.example.com:554/cam/realmonitor?channel=2&subtype=1
```

## 参数约束

- `brand` 和 `type` 在查找时不区分大小写；内置编码分别为 `Hikvision`、`Dahua` 和 `CAMERA`、`NVR`。
- `ip` 可以是主机名、IPv4 或 IPv6，不得包含空白或 URI authority 分隔符。IPv6 会自动添加方括号。
- `port` 必须是 `1` 到 `65535` 的数字。
- `account`、`password`、`channelNo` 和 `streamType` 必须非空。
- `channelNo` 只能包含数字；`streamType` 只能包含字母、数字、连字符和下划线。
- 参数非法、组合未知或处理器重复时抛出 `IllegalArgumentException`，错误消息不包含账号或密码。

## 自定义厂商

实现 `UrlAssemblyService` 并声明为 Spring Bean，即可增加新的品牌和设备类型组合：

```java
import com.ss.camera.domain.StreamUrlAssemblyDomain;
import com.ss.camera.service.UrlAssemblyService;
import org.springframework.stereotype.Component;

@Component
public class AcmeCameraUrlAssemblyService implements UrlAssemblyService {
    @Override
    public String brand() {
        return "Acme";
    }

    @Override
    public String type() {
        return "CAMERA";
    }

    @Override
    public String assembly(StreamUrlAssemblyDomain request) {
        // 自定义实现也应校验参数并正确编码 URI 组件。
        return "rtsp://camera.example/live/" + request.getChannelNo();
    }
}
```

同一品牌和类型只允许一个处理器。重复组合会在创建 `UrlAssemblyHolder` 时失败，防止处理结果依赖 Bean 顺序。

## 非 Spring 使用

四个内置组装器都是无状态类，可在普通 Java 程序中直接使用：

```java
UrlAssemblyHolder holder = new UrlAssemblyHolder(List.of(
        new HikCameraUrlAssemblyService(),
        new HikNvrUrlAssemblyService(),
        new DahuaCameraUrlAssemblyService(),
        new DahuaNvrUrlAssemblyService()));

String url = holder.assembly(request);
```

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-camera \
  -am clean verify
```

模块测试不连接真实摄像机，覆盖四种地址规则、凭据编码、通道规范化、参数失败、重复处理器和 Spring Boot 自动配置。

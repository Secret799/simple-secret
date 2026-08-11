# Simple Secret Geo Plugin

`simple-secret-plugin-geo` 是一个零第三方运行时依赖的 Java 17 插件，用于在图片像素坐标、WGS84 地理坐标和 DJI 相机遥测之间进行转换。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-geo</artifactId>
</dependency>
```

不使用 BOM 时显式指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-geo</artifactId>
    <version>1.1.0</version>
</dependency>
```

插件不依赖 Spring Boot、Lombok、Hutool、日志框架或 Honeybee 公共模块。

## 坐标和角度约定

- 地理坐标使用 WGS84，经纬度单位为度，海拔单位为米。
- 图片原点位于左上角，X 向右、Y 向下。
- 相机坐标系为 X 向右、Y 向下、Z 向镜头前方。
- NED 坐标系为 North、East、Down。
- yaw 为绝对航向角，`0` 指向正北，`90` 指向正东。
- pitch 为 `0` 时水平，负值向下，`-90` 为正下视。
- `groundAltitude` 是目标地面的绝对海拔，不是相对相机高度。
- 射线不与目标地面相交时，单点定位返回 `null`。

## 通用像素定位

先描述相机位置、姿态、视场角和画面尺寸：

```java
import com.ss.geo.GeoReferencer;
import com.ss.geo.domain.CameraState;
import com.ss.geo.domain.GeoTarget;
import com.ss.geo.domain.PixelCoordinate;

CameraState camera = new CameraState()
        .setLat(31.2304)
        .setLon(121.4737)
        .setAlt(120.0)
        .setGimbalYaw(30.0)
        .setGimbalPitch(-90.0)
        .setGimbalRoll(0.0)
        .setFovH(84.0)
        .setFovV(53.0)
        .setFrameWidth(4000)
        .setFrameHeight(3000);

GeoTarget target = GeoReferencer.pixelToGeo(
        new PixelCoordinate(2000, 1500), camera, 5.0);

if (target != null) {
    double latitude = target.getLat();
    double longitude = target.getLon();
    double slantDistance = target.getDistance();
}
```

反向把地理坐标投影到图片：

```java
import com.ss.geo.domain.GeoCoordinate;
import com.ss.geo.domain.PixelCoordinate;

PixelCoordinate pixel = GeoReferencer.geoToPixel(
        new GeoCoordinate(31.2305, 121.4738, 5.0), camera);

if (pixel != null) {
    boolean inside = pixel.getX() >= 0 && pixel.getX() < camera.getFrameWidth()
            && pixel.getY() >= 0 && pixel.getY() < camera.getFrameHeight();
}
```

目标位于相机后方时 `geoToPixel` 返回 `null`；目标在相机前方但画面外时仍会返回像素坐标，由调用方决定是否裁剪。

## 批量检测框定位

```java
import com.ss.geo.domain.BoundingBox;
import com.ss.geo.domain.GeoTargetWithBox;

List<BoundingBox> boxes = List.of(
        new BoundingBox(100, 200, 80, 120, "person", 0.95),
        new BoundingBox(600, 300, 160, 100, "vehicle", 0.88));

List<GeoTargetWithBox> targets = GeoReferencer.boxesToGeo(boxes, camera, 5.0);

for (GeoTargetWithBox target : targets) {
    if (target.isLocated()) {
        use(target.getBox(), target.getLat(), target.getLon());
    }
}
```

每个检测框使用中心点定位。返回结果与输入列表一一对应并保留原始 `BoundingBox`；通过 `isLocated()` 判断是否定位成功。未命中时不要读取默认的坐标数值。传入 `null` 列表返回可修改的空列表。

## DEM 地面海拔

地面起伏明显时，可以提供 DEM 查询回调。回调参数为 `[lat, lon]`，返回该位置的绝对海拔：

```java
GeoTarget target = GeoReferencer.pixelToGeo(
        new PixelCoordinate(2000, 1500),
        camera,
        latLon -> demService.altitude(latLon[0], latLon[1]),
        5.0);
```

DEM 查询失败或返回 `null`、`NaN`、无穷值时使用 `fallbackAltitude`。

## DJI 照片定位

插件直接解析 JPEG 中的 SOF、EXIF GPS 和 DJI XMP，不需要额外元数据依赖：

```java
import com.ss.geo.DjiPhotoGeoreferencer;
import com.ss.geo.domain.GeoTarget;

GeoTarget target = DjiPhotoGeoreferencer.refer(
        Path.of("/data/photos/DJI_0001.JPG"),
        2000,
        1500,
        5.0);
```

需要重复定位同一张图片时，只读取一次元数据：

```java
import com.ss.geo.photo.DjiMetadataReader;
import com.ss.geo.photo.DjiPhotoMetadata;

DjiPhotoMetadata metadata = DjiMetadataReader.read(photoPath);

GeoTarget first = DjiPhotoGeoreferencer.refer(metadata, 1200, 800, 5.0);
GeoTarget second = DjiPhotoGeoreferencer.refer(metadata, 2200, 1600, 5.0);
```

相机内参按以下顺序选择，前一项有效时不会继续使用后一项：

1. 显式 DJI XMP `drone-dji:CalibratedFocalLength`（唯一可设置标定焦距的来源）。
2. EXIF 焦平面分辨率与物理焦距。
3. 物理焦距与已识别相机传感器宽度。
4. 35mm 等效焦距。
5. DJI 机型和相机规格。
6. 默认水平/垂直视场角。

支持的 DJI 机型包括 M30、M30T、M3D、M3TD、M4D、M4TD、Mavic 3E、Mavic 3T 和 M350 RTK。元数据无法识别机型时，只要照片中存在有效焦距信息仍可定位。

普通 DJI GPS 会覆盖 EXIF GPS；只有 RTK 纬度、经度和绝对高形成完整合法三元组时，RTK 才整体覆盖普通 GPS。标准 EXIF 焦距字段仍用于后续 FOV 回退。MakerNote 是厂商私有结构，标签语义并不稳定，本模块刻意忽略它，绝不会猜测未知字段为标定焦距。

## DJI 实时遥测

```java
import com.ss.geo.DjiTelemetryGeoreferencer;
import com.ss.geo.spec.CameraType;
import com.ss.geo.spec.DjiCameraTelemetry;
import com.ss.geo.spec.DjiDroneModel;
import com.ss.geo.spec.DjiProjectionContext;

DjiCameraTelemetry telemetry = new DjiCameraTelemetry()
        .setLat(31.2304)
        .setLon(121.4737)
        .setAlt(120.0)
        .setFlightYaw(30.0)
        .setGimbalYaw(30.0)
        .setGimbalPitch(-90.0)
        .setGimbalRoll(0.0)
        .setFrameWidth(1920)
        .setFrameHeight(1080)
        .setProjectionContext(new DjiProjectionContext()
                .setDroneModel(DjiDroneModel.M3TD)
                .setCameraType(CameraType.ZOOM)
                .setZoomFactor(7.0));

GeoTarget target = DjiTelemetryGeoreferencer.pixelToGeo(
        telemetry, new PixelCoordinate(960, 540), 5.0);
```

实时遥测必须提供无人机型号和相机类型。`zoomFactor` 使用 DJI 绝对倍率，必须位于该相机规格允许范围；例如 M30 变焦相机的基准端为 5x，M3TD 变焦相机的基准端为 1x。指定机型不存在对应相机、倍率越界或姿态/位置非法时会抛出 `IllegalArgumentException`，不会静默使用错误视场角。规格 FOV 会按实际输出画幅居中裁切，支持 4:3 照片和 16:9 视频。

## 管线投影

```java
import com.ss.geo.domain.GeoCoordinate;
import com.ss.geo.domain.PipelineProjection;

List<GeoCoordinate> pipeline = List.of(
        new GeoCoordinate(31.2304, 121.4737, 5.0),
        new GeoCoordinate(31.2305, 121.4739, 5.0));

PipelineProjection projection = GeoReferencer.pipelineToPixels(
        "pipeline-a", pipeline, camera, 2.0);
```

`centerline` 保存中心线投影点，`area` 保存按 `bufferMeters` 生成的左右缓冲区多边形。每个 `PipelineProjectionPoint` 区分：

- `visible`：地理点是否位于相机前方。
- `insideFrame`：投影像素是否位于当前图片范围内。
- `pixel`：目标位于相机后方时为 `null`。

`null` 点会从中心线和缓冲区统一忽略。连续重复点仍保留在中心线结果中，但会从缓冲区计算中忽略。折角使用带上限的 miter，接近折返时使用 bevel，避免缓冲半径缩短或退化；少于两个有效不同坐标时缓冲区为空。

## 安全限制

- `Path` 和默认 `InputStream` 入口最多读取 64 MiB。
- 可通过 `DjiMetadataReader.read(inputStream, maxBytes)` 设置更小的调用方上限。
- JPEG 段长度会在读取前校验；损坏或越界的 TIFF/IFD 数据会被隔离并忽略，不影响其他 JPEG 段。
- DJI XMP 禁止 DTD 和外部实体，不会读取本地文件或网络资源。
- 未知 MakerNote 标签不会被猜测或提升为高优先级相机内参。
- 非 JPEG 输入抛出 `DjiMetadataReadException`。
- 读取器不会关闭调用方传入的 `InputStream`。
- 投影入口会校验有限像素、WGS84 经纬度、绝对海拔、相机姿态、FOV、内参和正画面尺寸；非法输入抛出 `IllegalArgumentException`。

Geo 插件只负责坐标和 DJI 元数据。KML/KMZ 航线解析由独立插件提供，不在本模块中引入 XML 航线模型或 Spring 自动配置。

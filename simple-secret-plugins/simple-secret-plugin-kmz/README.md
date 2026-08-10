# Simple Secret KMZ Plugin

`simple-secret-plugin-kmz` 是一个零第三方运行时依赖的 Java 17 插件，用于读写 KML、KMZ、DJI WPML 航点任务，以及读取普通 KML 中的 `LineString`。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-kmz</artifactId>
</dependency>
```

不使用 BOM 时显式指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-plugin-kmz</artifactId>
    <version>1.1.0</version>
</dependency>
```

模块不依赖 Spring Boot、Lombok、JSON、日志框架、Geo 或 Honeybee 公共模块。

## 创建并写出航点任务

领域对象保留 Honeybee 的无参构造、全参构造、链式 setter 和 `builder()` 风格：

```java
import com.ss.kmz.KmzWriter;
import com.ss.kmz.domain.Coordinate;
import com.ss.kmz.domain.KmzMission;
import com.ss.kmz.domain.MissionConfig;
import com.ss.kmz.domain.Waypoint;
import com.ss.kmz.domain.WaypointAction;
import com.ss.kmz.domain.WaypointHeading;

import java.nio.file.Path;
import java.util.List;

MissionConfig config = MissionConfig.builder()
        .flyToWaylineMode("safely")
        .finishAction("goHome")
        .exitOnRCLost("executeLostAction")
        .executeRCLostAction(1)
        .takeOffSecurityHeight(80)
        .globalTransitionalSpeed(10)
        .droneType(89)
        .payloadType(67)
        .globalRTHHeight(100)
        .build();

Waypoint waypoint = Waypoint.builder()
        .index(0)
        .coordinate(new Coordinate(116.3975, 39.9087, 80))
        .executeHeight(120)
        .waypointSpeed(8)
        .heading(new WaypointHeading(90))
        .actions(List.of(new WaypointAction(1, "takePhoto", null)))
        .build();

KmzMission mission = KmzMission.builder()
        .missionName("巡检任务")
        .missionConfig(config)
        .waypoints(List.of(waypoint))
        .build();

KmzWriter.write(mission, Path.of("inspection.kmz"));
```

`KmzWriter` 默认写出包含 `doc.kml` 的 KMZ。当前领域模型只覆盖 Honeybee 已有字段，不会虚构 DJI 完整双文件任务所需的 action group、payload 和模板参数。

只需要 KML 字符串时直接使用：

```java
import com.ss.kmz.KmlWriter;

String kml = KmlWriter.writeToString(mission);
```

写出前会校验任务名长度、航点序号、坐标、速度、高度、航向和动作。非法值抛出 `IllegalArgumentException`，不会生成部分文件。

## 读取 KML 和 KMZ

```java
import com.ss.kmz.KmlReader;
import com.ss.kmz.KmzParser;
import com.ss.kmz.domain.KmzMission;

KmzMission fromKml = KmlReader.parse(kmlText);
KmzMission fromKmz = KmzParser.parse(Path.of("inspection.kmz"));
```

KMZ 同时支持 `.kml` 和 `.wpml`。存在多个候选时按以下固定顺序选择：

1. `wpmz/waylines.wpml`
2. `wpmz/template.kml`
3. 根目录 `doc.kml`
4. 其他唯一的 `.wpml` 或 `.kml`

同一优先级存在重复项，或者最后一级有多个候选时，解析器抛出 `KmzException`，不会随机使用 ZIP 中的第一个文件。

## 读取普通 KML LineString

```java
import com.ss.kmz.kml.KmlLineString;
import com.ss.kmz.kml.KmlLineStringReader;

import java.util.List;

List<KmlLineString> lines = KmlLineStringReader.read(
        Path.of("pipelines.kml"));

for (KmlLineString line : lines) {
    String placemarkName = line.name();
    line.coordinates().forEach(coordinate -> {
        double longitude = coordinate.getLongitude();
        double latitude = coordinate.getLatitude();
        double altitude = coordinate.getAltitude();
    });
}
```

读取器支持 `MultiGeometry` 中的多个 `LineString`。二维坐标 `lon,lat` 的海拔默认为 `0`，结果列表和每条坐标列表均不可修改。

## 与 Geo 插件配合

KMZ 插件不会强制依赖 Geo。应用同时使用两个插件时显式映射坐标：

```java
import com.ss.geo.domain.GeoCoordinate;
import com.ss.kmz.kml.KmlLineString;

KmlLineString line = lines.get(0);
List<GeoCoordinate> geoPoints = line.coordinates().stream()
        .map(point -> new GeoCoordinate(
                point.getLatitude(),
                point.getLongitude(),
                point.getAltitude()))
        .toList();
```

随后可把 `geoPoints` 传给 `GeoReferencer.pipelineToPixels(...)`。这种显式适配保持两个插件都能独立使用，也避免循环依赖。

## 自定义读取限制

```java
import com.ss.kmz.KmzReadLimits;

KmzReadLimits limits = new KmzReadLimits(
        8 * 1024 * 1024,
        2 * 1024 * 1024,
        32,
        8 * 1024 * 1024);

KmzMission mission = KmzParser.parse(inputStream, limits);
KmzMission kmlMission = KmlReader.parse(kmlInputStream, 2 * 1024 * 1024);
```

默认限制：

- KMZ 压缩输入最大 64 MiB。
- 单个 ZIP 条目解压后最大 16 MiB。
- ZIP 所有文件条目累计解压最大 64 MiB。
- ZIP 最多 128 个条目。
- KML/WPML 最大 16 MiB。
- 单个任务最多 10000 个航点。
- 单个航点最多 128 个动作。

## 安全和异常契约

- XML 禁止 DTD、外部实体、外部 DTD 和外部 Schema。
- ZIP 条目拒绝绝对路径、反斜杠、空路径段、`.` 和 `..` 路径段。
- 坐标使用 WGS84，经度范围 `[-180, 180]`，纬度范围 `[-90, 90]`。
- 坐标输出固定使用点号小数，不受系统 Locale 影响。
- 数值格式错误、`NaN` 和无穷值不会静默回退为零。
- 格式、安全或资源限制问题统一抛出 `KmzException`；调用方构造的领域对象非法时抛出 `IllegalArgumentException`。
- `KmlReader`、`KmlLineStringReader` 和 `KmzParser` 不关闭调用方传入的 `InputStream`。
- `KmzWriter.writeToStream` 不关闭调用方传入的 `OutputStream`。

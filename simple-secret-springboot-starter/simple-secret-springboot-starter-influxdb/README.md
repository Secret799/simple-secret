# Simple Secret InfluxDB Starter

`simple-secret-springboot-starter-influxdb` 面向 Java 17、Spring Boot 3.5 和 InfluxDB 1.x，提供注解实体映射、单条与批量写入、安全 InfluxQL DSL、查询结果映射、分页、通用 Service，以及数据库和 retention policy 的显式初始化。

生产直接依赖只有 `influxdb-java`、其 HTTP 超时配置所需的 OkHttp、`simple-secret-common-toolbox`、SLF4J API 和 Spring Boot 自动配置。OkHttp 本身也是 influxdb-java 的运行时依赖，显式声明不会增加新的传递 artifact。不依赖 JSON、Hutool、Guava、Lombok、Honeybee Core 或其他 starter。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-influxdb</artifactId>
</dependency>
```

未使用 BOM 时指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-influxdb</artifactId>
    <version>1.1.0</version>
</dependency>
```

## 安全默认配置

starter 默认关闭，不创建客户端，也没有默认 URL、账号、密码、数据库或 retention policy。启用时必须显式配置 URL 和数据库：

```yaml
simple-secret:
  influxdb:
    enabled: true
    url: http://localhost:8086
    database:
      name: metrics
    connect-timeout-millis: 10000
    read-timeout-millis: 10000
    write-timeout-millis: 10000
    consistency: one
    log-level: none
```

凭据应由环境变量或密钥系统注入：

```yaml
simple-secret:
  influxdb:
    username: ${INFLUXDB_USERNAME:}
    password: ${INFLUXDB_PASSWORD:}
```

配置密码却没有用户名会使启动失败。URL 不允许携带 userinfo 或 fragment。生产环境应使用 HTTPS、最小权限账号，并保持 `log-level: none`；`FULL` 级别可能记录敏感 HTTP 信息。

## 定义实体

使用 `influxdb-java` 原生注解。实体必须有无参构造器才能映射查询结果，但不要求继承任何基类：

```java
import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.annotation.TimeColumn;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Measurement(name = "telemetry")
public class Telemetry {
    @Column(name = "device_id", tag = true)
    private String deviceId;

    @Column
    private Double value;

    @TimeColumn(timeUnit = TimeUnit.MILLISECONDS)
    private Instant createdAt;

    public Telemetry() {
    }

    public Telemetry(String deviceId, Double value, Instant createdAt) {
        this.deviceId = deviceId;
        this.value = value;
        this.createdAt = createdAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public Double getValue() {
        return value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

`@Measurement(database = "...")` 和 `retentionPolicy = "..."` 可覆盖默认目标。未指定时使用 starter 配置；`autogen` 注解默认值不会被强制写入，允许服务器使用自己的默认策略。

可选继承 `BaseInfluxModel`，获得 measurement、database 和 retention policy 的便捷读取方法。映射器仍然支持不继承它的普通 POJO。

## 写入数据

注入 `InfluxOperations`：

```java
import com.ss.influxdb.client.InfluxOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class TelemetryWriter {
    private final InfluxOperations operations;

    public TelemetryWriter(InfluxOperations operations) {
        this.operations = operations;
    }

    public void write() {
        operations.save(new Telemetry("device-a", 12.5, Instant.now()));
        operations.saveBatch(List.of(
                new Telemetry("device-b", 13.5, Instant.now()),
                new Telemetry("device-c", 14.5, Instant.now())));
    }
}
```

null tag、field 和 time 会被跳过，不会静默转换为零或空字符串。Point 最终没有任何非空 field 时会在网络调用前失败。

`saveBatch()` 会按 database 和 retention policy 分组，每组作为一个同步 `BatchPoints` 请求提交。默认 batch 关闭；显式开启客户端 batch 后，`save()` 会进入 influxdb-java 的异步有界批处理队列：

```yaml
simple-secret:
  influxdb:
    batch-write:
      enabled: true
      actions: 1000
      flush-duration-millis: 1000
      consistency: one
```

应用关闭时 Spring 会调用客户端 `close()`，由 influxdb-java 刷新并关闭 batch。异步失败日志只记录异常类型，不记录 Point 内容。

## 安全 Lambda 查询

```java
import com.ss.influxdb.query.LambdaQueryWrapper;

import java.time.Instant;
import java.util.List;

LambdaQueryWrapper<Telemetry> query = operations.wrapper(Telemetry.class)
        .select(Telemetry::getDeviceId, Telemetry::getValue)
        .eq(Telemetry::getDeviceId, "device-a")
        .between(Telemetry::getValue, 10, 20)
        .ge(Telemetry::getCreatedAt, Instant.parse("2026-08-10T00:00:00Z"))
        .notIn(Telemetry::getDeviceId, List.of("retired", "deleted"))
        .orderByTimeDesc()
        .limit(100);

List<Telemetry> records = operations.list(query);
Telemetry one = operations.one(
        operations.wrapper(Telemetry.class)
                .eq(Telemetry::getDeviceId, "device-a")
                .limit(1));
```

支持 `eq/ne/gt/ge/lt/le/between/in/notIn`、嵌套 `and/or`、聚合函数、分组、时间排序、limit、offset 和分页。字符串中的单引号与反斜杠会转义；identifier、函数名、duration 和数值类型会集中校验；DSL 不接受任意 raw 条件片段，也不接受可覆盖 `toString()` 的任意 `Number` 子类。

聚合示例：

```java
String influxql = operations.wrapper(Telemetry.class)
        .function("mean", Telemetry::getValue, "mean_value")
        .eq(Telemetry::getDeviceId, "device-a")
        .groupByTime("5m")
        .build();
```

InfluxQL 的时间窗口分组必须配合聚合或转换函数。按 tag 分组时，`limit/offset` 是逐 series 生效；DSL 保留该原生能力，但 `page()` 会拒绝 tag 分组，防止把逐 series 分页误认为全局分页。

动态 measurement 和 retention policy 仍使用标识符校验：

```java
operations.wrapper(Telemetry.class)
        .measurement("telemetry_2026")
        .retentionPolicy("archive")
        .limit(10)
        .build();
```

## 分页

InfluxQL 没有可靠的通用行计数函数。分页 API 要求显式指定一个业务上始终非空的普通 field，使用 `COUNT(field)` 统计。tag 和 time 不能作为计数字段，分页查询也不能包含 tag 或时间分组：

```java
import com.ss.influxdb.domain.InfluxPage;

InfluxPage<Telemetry> page = operations.page(
        operations.wrapper(Telemetry.class)
                .ge(Telemetry::getCreatedAt, Instant.parse("2026-08-01T00:00:00Z")),
        Telemetry::getValue,
        2,
        50);

long total = page.getTotal();
long totalPages = page.getTotalPages();
List<Telemetry> data = page.getRecords();
```

不要选择可能为 null 的 field 计数，否则 InfluxDB 会按非空值语义返回较小的总数。总数为 0 时不会再发送数据查询；分页结果及 records 列表均不可变。

## 通用 Service

业务 Service 可继承 `BaseInfluxService`，实体类型通过构造器显式传入，不依赖 Spring 代理泛型解析：

```java
import com.ss.influxdb.client.InfluxOperations;
import com.ss.influxdb.service.BaseInfluxService;
import org.springframework.stereotype.Service;

@Service
public class TelemetryService extends BaseInfluxService<Telemetry> {
    public TelemetryService(InfluxOperations operations) {
        super(operations, Telemetry.class);
    }
}
```

`TelemetryService` 直接提供 `save`、`saveBatch`、`list`、`one`、`page`、`query` 和 `wrapper`。

## 原始 InfluxQL

高级调用方可以执行完整查询：

```java
QueryResult result = operations.query(
        "SHOW MEASUREMENTS",
        "metrics");

List<Telemetry> records = operations.list(
        "SELECT * FROM \"telemetry\" LIMIT 10",
        Telemetry.class);
```

原始 InfluxQL 必须来自可信代码。不要把 HTTP 参数、设备上报字段或用户输入直接拼接到 SQL；需要动态条件时使用 Lambda DSL。统一异常不会把完整 SQL、服务端原始错误、Point 或凭据写入异常消息。

## 数据库和 Retention Policy

运行期可显式管理：

```java
if (!operations.databaseExists("metrics")) {
    operations.createDatabase("metrics");
}

if (!operations.retentionPolicyExists("metrics", "archive")) {
    operations.createRetentionPolicy("metrics", "archive", "30d", 1, false);
}
```

自动创建默认关闭。只有显式打开时，启动阶段才先创建数据库，再创建 retention policy；Spring 提供的 `InfluxOperations` 会等待初始化完成后才允许业务 Bean 注入使用，任一步失败都会阻止应用启动：

```yaml
simple-secret:
  influxdb:
    database:
      name: metrics
      auto-create: true
    retention-policy:
      name: archive
      duration: 30d
      replication: 1
      default-policy: false
      auto-create: true
```

标识符和 duration 在发送命令前校验。数据库与 retention policy 管理命令不携带默认数据库上下文，允许在目标数据库尚未创建时执行。启用 retention policy 自动创建但数据库不存在、且数据库自动创建关闭时，应用会明确失败。

## 自定义客户端

应用提供自己的 `InfluxDB` Bean 时，starter 不再创建或配置客户端，因此不要求 URL 和凭据；仍需配置默认数据库供 `InfluxOperations` 使用：

```java
@Bean
InfluxDB influxDB() {
    return customInfluxDBClient;
}
```

也可以只替换 `InfluxClientFactory`，保留 starter 的超时、日志、一致性、batch 和关闭生命周期配置。

## 非 Spring 使用

纯 Java 程序可直接组合同一套映射和操作能力：

```java
InfluxdbProperties properties = new InfluxdbProperties();
properties.setEnabled(true);
properties.setUrl("http://localhost:8086");
properties.getDatabase().setName("metrics");

InfluxDB client = new DefaultInfluxClientFactory().create(properties);
client.setLogLevel(properties.getLogLevel());
client.setConsistency(properties.getConsistency());

InfluxMetadataRegistry registry = new InfluxMetadataRegistry();
InfluxOperations operations = new InfluxOperations(
        client,
        properties,
        registry,
        new InfluxPointMapper(registry),
        new InfluxResultMapper(registry));

try {
    operations.save(new Telemetry("device-a", 12.5, Instant.now()));
} finally {
    client.close();
}
```

非 Spring 场景由调用方负责客户端配置与关闭。启用 batch 时还应使用 `BatchOptions` 显式配置，并确保最终调用 `close()`。

## 结果映射与错误边界

- 支持继承字段、注解列名、分组 tags、数字转换和 ISO 时间映射。
- 未声明的聚合列会忽略；已声明列转换失败会抛 `InfluxOperationException`。
- QueryResult 顶层或子结果包含 server error 时视为失败，不返回伪成功空列表。
- `one()` 返回多行时抛错，返回零行时为 `null`。
- 反射读取、实例化或写入失败不会吞掉，也不会向结果列表加入 null。
- 配置、元数据、DSL 和 Point 错误尽量在网络调用前失败。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-influxdb test
```

模块测试不依赖真实 InfluxDB，覆盖自动配置、发布依赖、配置模型、实体映射、Point 映射、查询 DSL、分页、初始化和客户端错误边界。真实部署仍应在目标 InfluxDB 版本上验证 retention policy、权限和网络超时配置。

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

simple-secret 是一个帮助开发者快速接入部分技术框架的插件集（`com.ss` 组，版本 `1.0.0`）。技术栈：**Java 17 + Maven 多模块 + Spring Boot 3.2.6**。

- 版本统一由根 pom 的 `${revision}` 属性管理（当前 `1.0.0`），所有子模块的 `<version>` 都引用它。升级版本只改根 pom 和 `simple-secret-common-bom` 的 `revision`。
- 所有源码注释、javadoc、README 均为中文，新增代码应保持中文 javadoc。
- 仓库没有 maven wrapper，使用系统 `mvn`（3.9+）。依赖与插件仓库指向自建 nexus `https://repository.junpzx.cn/`，若本地仓库已缓存可加 `-o` 离线构建。
- 每个模块根目录的 `.flattened-pom.xml` 是 `flatten-maven-plugin` 生成的产物，不要手工编辑。
- 根 pom 启用了 lombok 注解处理器（`lombok.version` 需兼容当前 JDK，本机 JDK 26 用 1.18.44+），**只有** `simple-secret-springboot-starter-zlm4j` 使用 lombok；其余模块保持无 lombok、手写 getter/setter 的风格。

## 模块架构

四层聚合模块（顶层 `pom`；功能实现统一收敛到 starter）：

| 目录 | 职责 |
|---|---|
| `simple-secret-common` | 内部公共工具与对外依赖管理 BOM |
| `simple-secret-plugins` | 纯 Java 插件聚合模块，不引入 Spring 自动配置 |
| `simple-secret-springboot-starter` | 功能实现与 Spring Boot 自动配置的唯一发布边界 |
| `simple-secret-application` | 可直接运行的应用程序；目前**只有空 jar 产物，没有源码** |

叶子模块：

- `common/simple-secret-common-toolbox` — 纯工具，无第三方依赖。`SerializableFunction` + `LambdaPropertyResolver`/`SerializedLambdaExtractor` 通过 `SerializedLambda` 把 getter 方法引用解析为属性名/字段。
- `common/simple-secret-common-bom` — 对外依赖管理与版本定义（flatten 模式为 `bom`）。
- `plugins/simple-secret-plugin-geo` — 零运行时依赖的像素/WGS84 坐标转换、DJI 照片与遥测投影、管线投影及安全 EXIF/XMP 读取（包 `com.ss.geo`）。
- `springboot-starter/simple-secret-springboot-starter-json` — JSON 核心与自动配置（包 `com.ss.json`）。
- `springboot-starter/simple-secret-springboot-starter-mqttv5` — MQTT v5 核心与自动配置（包 `com.ss.mqttv5`，Paho v5 1.2.5）。
- `springboot-starter/simple-secret-springboot-starter-zlm4j` / `-easymedia` — 对应功能与自动配置。

## JSON Starter

**核心约定：两套 mapper 互不相通。**
- `JsonUtils` 是静态门面，始终使用 `DefaultObjectMapperFactory` 创建的独立默认 mapper，**不接收** Spring 容器中的 Jackson 定制（如 Web 层的 `ObjectMapper` 自定义）。非 Spring 代码用它。
- `JsonCodec` 包装调用方传入的 mapper。Spring Boot 代码应注入容器中的 `JsonCodec` Bean（由 `SimpleSecretJsonCodecAutoConfiguration` 提供，底层是 Spring 托管的 `ObjectMapper`）。`MqttMessageContext.getPayload()` 内部走的是 `JsonUtils`。
- 两条自动配置都注册在 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（用 imports 文件而非 spring.factories），可用 `simple-secret.json.enabled=false` 整体关闭。

统一 JSON 规则（`SimpleSecretJsonModule`）：Long/BigInteger 超出 JS 安全整数范围（±9007199254740991）时序列化为字符串（`SafeIntegerSerializer`），BigDecimal 序列化为字符串，LocalDateTime 固定格式 `yyyy-MM-dd HH:mm:ss`。`SimpleSecretJacksonAutoConfiguration` 以 `Jackson2ObjectMapperBuilderCustomizer` 增量应用这些规则到 Spring 管理的 mapper，并关闭 `FAIL_ON_UNKNOWN_PROPERTIES` 与 `WRITE_DATES_AS_TIMESTAMPS`。

`JsonPropertyNameResolver`（配合 toolbox 的 `LambdaPropertyResolver`）把 getter 方法引用解析成 JSON 属性名，字段上的非空 `@JsonProperty` 优先。

## MQTT v5 Starter

核心入口是 `MqttClientManager`（`com.ss.mqttv5.client`）：管理多个客户端（key 为字符串，默认 `default`）的连接、断线重连、发布、订阅、同步请求响应。它与 Paho 之间隔着一层最小适配接口 `MqttClientAdapter`（`PahoMqttClientAdapter` 为真实实现，测试用 `FakeMqttClientAdapter`，因此测试不需要真实 broker），由 `MqttClientFactory` 创建。三个线程池（publish / handler / connection，均为 `ScheduledExecutorService` 等）由外部注入，manager 不创建它们。

关键设计点：

- **入站路由**：`MqttMessageHandler` 接口定义 `clientKey()`（默认 `default`）、`topic()` 过滤器、`qos()`、`shareGroup()`、`handle(MqttMessageContext)`。`MqttClientContext` 按 topic filter 匹配分发到处理器，带基于版本的 handler 缓存。
- **同步请求响应**：`request` / `requestWithRetry` 发布请求 → 临时订阅 reply filter（引用计数，`temporarySubscriptionCounters`）→ `MqttResponseWaiter`（`DefaultMqttResponseWaiter` 基于 `CompletableFuture`）等待关联响应。`MqttCorrelationExtractor`/`MqttCorrelationType` 负责从请求/响应提取关联键，`MqttRequestContext` 维护等待注册。
- **配置指纹**：`MqttClientManager.refreshClients` 对 `MqttClientOptions` 做 SHA-256 指纹，配置未变则复用已有连接；`options` 校验不合法会抛 `IllegalArgumentException`（如 broker 为空、超时非正数）。
- **主题工具**：`MqttTopics` 提供 topic/filter 校验、共享订阅（`$share/group/topic`）拼接与匹配。
- **客户端 ID**：`MqttClientOptions.resolveClientId()` 未显式配置时返回为该实例生成的稳定 UUID。
- 配置 POJO（`MqttClientOptions`、`MqttWillOptions`）是普通 getter/setter 类，供 Spring Boot 属性绑定，没有校验注解。

### Spring Boot 自动配置

`SimpleSecretMqttAutoConfiguration`（前缀 `simple-secret.mqtt`，`enabled=false` 可关闭）按顺序提供：三个线程池 Bean（`mqttPublishExecutor` 满了直接拒绝 AbortPolicy；handler 用 CallerRunsPolicy）→ `MqttResponseWaiter` → `MqttClientManager` → `MqttClientRefresher` → `MqttLifecycle` → `MqttConfigurationRefreshListener`。全部 `@ConditionalOnMissingBean`，可被外部覆盖。

生命周期：`MqttLifecycle`（`ApplicationRunner` + `AutoCloseable`）在应用启动后调用 `MqttClientRefresher.refresh()`，容器销毁时关闭全部客户端。`refresh()` 从 `MqttProperties.clients` 中筛选 `enabled: true` 的客户端交给 manager，并在每个客户端连接成功后把 `clientKey()` 匹配的处理器订阅进去（见 README 的配置与 handler 示例）。

`MqttConfigurationRefreshListener` 通过**反射**识别 Spring Cloud 的 `EnvironmentChangeEvent`（starter 不直接依赖 Spring Cloud，`MqttProperties` 键前缀 `simple-secret.mqtt`），只有该前缀下的配置键变化才触发刷新；测试里 `org/springframework/cloud/context/environment/EnvironmentChangeEvent.java` 是复制的桩类，用于模拟该事件。

## ZLMediaKit 插件（starter-zlm4j）

`com.ss.zlm4j`，**从 honeybee 的 `honeybee-plugins-zlm4j-starter` 整体迁移而来**（`com.secret.honeybee.plugins.zlm4j`），保留 lombok、javacv/opencv/ffmpeg 等依赖，hutool 已全部替换为原生 Java。将 ZLMediaKit 通过 zlm4j（JNA）以嵌入式服务器运行。包内结构：

- **配置加载**：`simple-secret-zlm4j.yml` + `simple-secret__zlm4j-default__conf.ini` 不是 `application*.yml`，全靠 `@SimpleSecretPropertySource` 注解 + `SimpleSecretPropertySourcePostProcessor`（BeanFactoryPostProcessor，注册在 AutoConfiguration.imports）加载进 Environment；ini 由 `SimpleSecretIniPropertySourceLoader`（注册在 spring.factories 的 `PropertySourceLoader`）解析，文件名 `__` 推导前缀 `simple-secret.zlm4j-default`。`ZlmMediaProperties` 绑定 `simple-secret.zlm4j.*`，默认开启，`enabled=false` 关闭。
- **生命周期**：`SimpleSecretZlmAutoConfiguration` 创建 `ZlmMediaContext`（`@PostConstruct` 里 `Native.load("mk_api")` 加载 ZLMediaKit 原生库并启动 HTTP/RTSP/RTMP/RTC 服务，`@PreDestroy` 停止）与 `ZlmCallbackHandlerContext`。`zlmScheduledExecutor` 是模块自带的 daemon 调度线程池（转码/拼接用）。
- **回调层**：`ZlmMediaContext` 把各 ZLM hook 绑定到 `MK*CallBack`，回调经 `ZlmCallbackHandlerContext` 里按 `@Order` 排序、反转后注册的 `ZlmCallbackHandlerRegister` 拿到处理器（默认是 `handler.impl.Default*Handler`），随后发布 Spring 事件（`com.ss.zlm4j.event`）。
- **服务层**：`IZlmMediaService`（推拉流代理/录像/RTP/统计/配置）、`ISnapService`、`ITranscodeService`、`IVideoStackService`。`ZlmMediaHelper` 提供 `Assembler`（zlm 结构→domain）与 `Configurator`（写 `MK_INI`）。异常统一为 `ZlmOperationException`。
- **支撑类**：`com.ss.zlm4j.support.SpringUtils`（ApplicationContextAware，`getBean`/`publishEvent`/`getSimpleSecretScheduledExecutor`）；zlm 服务通过它取 `ZlmMediaContext`。
- **运行时前提**：ZLMediaKit 原生库 `mk_api` 由部署环境提供；javacv 平台包只声明 windows/linux x64。

## EasyMedia 插件（starter-easymedia）

`com.ss.easymedia`，**从 honeybee 的 `honeybee-module-easy-media` 迁移而来**（`com.secret.honeybee.module.easymedia`），无 lombok、无 hutool、无 Sa-Token、无 honeybee-common 依赖；WebRTC 会话与限流改用**通用 `org.redisson:redisson`** 依赖（`RedissonClient` 由调用方提供，未配置时回退单机内存实现 `InMemoryWebRtcSessionRepository` + 空限流器）。依赖 `starter-zlm4j`，因此 `simple-secret.zlm4j.enabled` 必须为 true。

- **自动配置**：`SimpleSecretEasyMediaAutoConfiguration`（注册在 AutoConfiguration.imports）前缀 `simple-secret.easymedia`，`enabled=false` 关闭；`@SimpleSecretPropertySource` 加载 `simple-secret-easymedia.yml`（非 `application*.yml`）。`WebRtcSessionConfiguration` 由它 `@Import`，前缀 `simple-secret.easymedia.webrtc`。
- **回调层**：`emsCallbackHandlerRegister`（实现 `ZlmCallbackHandlerRegister` + `Ordered`，order=0）在 `ZlmCallbackHandlerContext` 注册 `EmsStreamNoReaderAppDispatcher`/`EmsStreamNoFoundAppDispatcher`；`AppHandler` 体系按 app 作用域分发（`live`/`dibbling`/`record`）。`TrackDelegateCallback` 提供轨道帧委托监听（SEI 提取用），`EmsCommonStreamChangeHandler` 分发已就绪但默认未启用。
- **WebRTC 网关**：`Zlm4jWebRTCController`（WHIP/WHEP 信令 + 会话 PATCH/DELETE）→ `WebRtcSessionService` 编排鉴权、限流、上游信令与 Redis 会话。身份解析 `DefaultWebRtcIdentityProvider`：配置 `security.default-subject` 即用固定主体认证，否则按 `authentication-required` 拒绝或按 IP 构造匿名身份；业务方可注入自定义 `WebRtcIdentityProvider`/`WebRtcAccessPolicy`。`WebRtcSessionCleanupJob`（`@Scheduled`）补偿失败的上游 DELETE。
- **ZLM 通用管理**：`ApiController`（`/easyMedia/api/common/*`）直接返回业务值（已去除 honeybee 的 `Result` 包裹），失败抛 `ZlmOperationException`。`H264NakedFlowPushZlmManager` 推送 H.264 裸流，配 `MemoryTimeCacheManager`（内联实现，剥离 hutool）。`support/udp` 为内联的 UDP 组播工具。
- **指标**：`simple-secret.webrtc.*` 前缀（低基数标签，无 sessionId/app/stream/租户/IP）。

## 构建与测试

```bash
# 全量构建（含测试、javadoc、source jar）
mvn clean install

# 只跑某个模块及其依赖（离线可用）
mvn -pl simple-secret-common/simple-secret-common-toolbox -am test

# 单个测试类
mvn -pl simple-secret-springboot-starter/simple-secret-springboot-starter-mqttv5 -Dtest=MqttClientManagerOperationsTest test

# 跳过测试打包
mvn clean package -DskipTests
```

测试约定：JUnit 5（junit-jupiter）+ AssertJ 断言；自动配置测试用 Spring Boot 的 `ApplicationContextRunner` + `AutoConfigurations`（如 `SimpleSecretMqttAutoConfigurationTest`）；MQTT 测试通过 `FakeMqttClientAdapter` 注入 `MqttClientFactory` 的包私有构造函数，不连真实 broker。MQTT starter 内有些包私有成员（如 `MqttClientContext`）仅供同包测试访问。

zlm4j 模块没有测试原生库（`ZlmMediaContext` 的 `@PostConstruct` 会 `Native.load("mk_api")`，无原生库直接失败），所以自动配置测试只断言 `enabled=false` 时不创建需要原生库的 Bean；ini 前缀机制用 `SimpleSecretIniPropertySourceLoader` 的纯单元测试覆盖。

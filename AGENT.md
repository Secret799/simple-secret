# Simple Secret Repository Guidelines

## 项目定位

Simple Secret 是一组按需引入的 Java 17 公共组件、纯 Java 插件和 Spring Boot 3.5 starter。项目强调最小依赖、
明确的模块边界和可控的资源生命周期。不要把聚合 POM 当作业务运行时依赖，也不要为单个能力引入整个项目。

开始修改前先阅读根目录 `README.md`、目标模块的 `README.md` 和 `pom.xml`。模块文档描述了公开 API、配置项、
资源所有权和安全边界，应与代码同步维护。

## 模块结构与依赖方向

- `simple-secret-common`：公共模块聚合器。
  - `simple-secret-common-core`：Result、异常、HTTP 状态码和校验分组，零第三方生产依赖。
  - `simple-secret-common-toolbox`：Lambda 属性、URI、缓存、动态列和时间工具，零第三方生产依赖。
  - `simple-secret-common-dict`：字典注册、查询、缓存和字段翻译，只依赖 toolbox。
  - `simple-secret-common-bom`：统一管理 Simple Secret 模块及关键第三方依赖版本。
- `simple-secret-plugins`：不依赖 Spring 容器的插件，包括 Geo、KMZ、UDP、Excel 和 Camera SDK。
- `simple-secret-springboot-starter`：Spring Boot 自动配置模块，包括 MQTT、Camera、NATS、InfluxDB、
  Netty WebSocket、ZLM、EasyMedia 和 DJI 等能力。
- `simple-secret-application`：本地诊断、测试和推流应用，不作为可复用库发布。
- `integration-tests`：独立 consumer 工程，从已安装制品验证公开 API、传递依赖和自动配置；它不属于根 reactor。

依赖必须保持单向：application -> starter/plugin/common，starter -> plugin/common，plugin -> common/JDK。
common 和 plugin 不得反向依赖 starter 或 application；纯 Java 模块不得为了便利引入 Spring。所有 Java 包使用
`com.ss.<模块缩写>.*`。

## 构建与验证命令

仓库没有 Maven Wrapper，使用系统 `mvn`。根 POM 通过 Enforcer 要求 JDK 17，其他 JDK 版本不受支持；当前
macOS 开发环境执行 Maven 前应显式选择 Homebrew 的 JDK 17。

```bash
# 当前开发机默认 JDK 不是 17，先为本次终端会话切换版本
export JAVA_HOME=/opt/homebrew/opt/openjdk@17

# 完整编译和单元测试
mvn clean verify

# 只验证目标模块及其依赖，例如 MQTT v5 starter
mvn -pl simple-secret-springboot-starter/simple-secret-springboot-starter-mqttv5 -am test

# 只验证一个聚合层
mvn -pl simple-secret-common -am verify
mvn -pl simple-secret-plugins -am verify
mvn -pl simple-secret-springboot-starter -am verify

# 从第三方消费者视角验证已安装制品
mvn install -DskipTests
mvn -f integration-tests/pom.xml test
```

优先运行覆盖改动模块的最小命令；修改父 POM、BOM、公开依赖或跨模块契约时运行 `mvn clean verify`，随后运行
独立 consumer 测试。只有明确要求发布时才执行 `mvn deploy`。

## 编码与模块约定

- 使用 Java 17 和现有代码风格；没有统一格式化插件时，以相邻源码为准，不做无关格式化。
- 保持公开 API 小而明确。一次性逻辑不要提前抽象，新增依赖前确认 JDK 或现有模块不能满足需求。
- 聚合 POM 只组织模块，不向业务代码提供运行时 API。应用应依赖具体 artifact，并优先导入
  `simple-secret-common-bom` 管理版本。
- 新增或调整可发布模块时，同步检查根 `dependencyManagement`、common BOM、模块 README 和 consumer 测试。
- 不要手工编辑或提交 Maven 生成的 `.flattened-pom.xml`、`target/`、日志、IDE 文件或本地运行产物。
- Lombok 当前只用于确有依赖的模块（主要是 zlm4j）；不要在其他模块扩散使用。

## Spring Boot Starter 约定

- 自动配置使用 Spring Boot 3 的 `@AutoConfiguration`，并注册到
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。
- 配置统一使用 `simple-secret.*` 前缀。新增或修改 `@ConfigurationProperties` 时同步验证配置元数据。
- 连接外部服务、监听端口、启动线程或加载原生库的能力必须默认关闭，并通过明确配置启用。
- 默认 Bean 应允许宿主通过同类型 Bean 或模块既有扩展点覆盖；不要造成跨 starter 的隐式启动或反向依赖。
- Spring 创建的连接、线程、套接字和原生资源必须纳入容器生命周期，并在关闭时可靠释放。
- 自动配置测试优先使用 `ApplicationContextRunner`，同时覆盖关闭状态、启用状态、条件缺失、配置校验和 Bean 覆盖。

## 插件、网络与原生资源

- 纯 Java 插件不得依赖 Spring。资源型 API 使用明确的 `start`、`stop`、`open`、`close` 或
  `AutoCloseable` 契约。
- 网络、文件、线程和 JNA/native 句柄必须在 `finally`、try-with-resources 或受管生命周期中释放；异常路径和
  重复关闭也要测试。
- 回调中的原生缓冲区若不能保证生命周期，必须先复制再交给业务线程。耗时消费使用有界执行器，避免阻塞 SDK
  回调线程或创建无界任务。
- 外部输入要校验长度、数量、格式、路径、压缩条目和容量上限，防止目录穿越、压缩炸弹、内存失控和资源泄漏。
- 厂商 SDK 路径、设备地址、密码、令牌和证书必须由外部配置提供，不能写入源码、测试夹具或仓库配置。

## 测试要求

- 使用 JUnit 5；断言库沿用目标模块现有选择（JUnit Assertions 或 AssertJ），模拟方式沿用现有测试。
- 测试类命名采用现有的 `*Test.java`，放在对应模块的 `src/test/java`；测试资源放在 `src/test/resources`。
- 修复缺陷时先增加能复现问题的测试。新增功能至少覆盖正常流程、无效输入、边界条件和资源清理。
- 涉及异步、网络或生命周期的测试必须有确定的超时和清理逻辑，不依赖执行顺序、真实设备或外部服务。
- 修改 starter 发布面时，检查自动配置 imports、配置元数据、公开依赖策略和对应 `integration-tests/consumer-*`。

## 文档、提交与安全

- 公开 API、依赖坐标、配置前缀、默认值、启停方式或兼容性变化必须同步更新目标模块 README；跨模块变化同时更新
  根 README。
- 提交信息遵循仓库历史中的 `type(scope): 中文摘要`，例如 `fix(zlm4j): 释放媒体源轨道引用`。
- 不提交真实凭据、设备信息、私钥、证书、厂商 SDK 二进制或本机绝对路径。示例配置必须使用安全占位值。
- 不削弱 Enforcer 的依赖收敛或禁用依赖规则来绕过构建；应修正依赖树和 BOM 约束。

## 工作方式

1. 先确认需求、模块边界和现有实现；不清楚且会影响公开契约时先提出问题。
2. 只修改完成任务所需的文件，保留工作区中已有且无关的变更。
3. 优先选择最简单的可维护实现，不为假设中的未来需求增加抽象或依赖。
4. 用与风险相称的测试验证结果，并明确说明未执行或依赖外部环境的检查。

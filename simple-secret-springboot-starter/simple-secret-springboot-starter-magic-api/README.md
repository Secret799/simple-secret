# Simple Secret Magic API Starter

`simple-secret-springboot-starter-magic-api` 为 Java 17、Spring Boot 3.5 应用提供受控的 Magic API 2.2.2 集成。starter 默认关闭，不创建资源目录、动态路由、WebSocket 或后台任务；只有显式设置 `simple-secret.magic-api.enabled=true` 才允许上游自动配置加载。

该模块不迁移 Honeybee 的 Result、MyBatis-Plus 分页、task 插件或 springdoc 插件，也不依赖 Simple Secret JSON starter、Honeybee core/doc/toolbox 或 Lombok。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-magic-api</artifactId>
</dependency>
```

未使用 BOM 时指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-magic-api</artifactId>
    <version>1.1.0</version>
</dependency>
```

Magic API 核心需要 Spring MVC、WebSocket 和 JDBC 类型，这些是上游运行时边界，不能由薄封装消除。starter 已排除 JavaEE Servlet 适配，只保留 Spring Boot 3 使用的 Jakarta Servlet 适配；同时排除上游未实际引用的 BeanUtils、Commons IO 和 Commons Text 直接声明，并由项目统一管理 Commons Lang 与 Commons Compress 的安全版本。Commons Compress 的 ZIP 实现仍需要 Commons IO，因此最终 classpath 会包含项目统一到 `2.22.0` 的 Commons IO，而不是上游声明的旧版本。

## 默认行为

仅添加依赖不会启动 Magic API：

```yaml
simple-secret:
  magic-api:
    enabled: false
```

显式启用后，starter 以最低优先级提供以下安全默认值，应用配置、环境变量和命令行参数可以覆盖它们：

```yaml
magic-api:
  banner: false
  support-cross-domain: false
  show-sql: false
  show-url: false
  resource:
    readonly: true
  page:
    max-page-size: 1000
```

starter 不提供默认文件目录。文件资源模式启用时必须显式设置 `magic-api.resource.location`，否则应用在创建任何 Magic API Bean 前启动失败。这样可以避免上游默认在 `/data/magic-api/` 创建可写目录。

## 只读文件资源

最小安全配置：

```yaml
simple-secret:
  magic-api:
    enabled: true

magic-api:
  resource:
    type: file
    location: ${MAGIC_API_HOME:./data/magic-api}
    readonly: true
```

只读模式适合由制品、配置管理或外部发布流程提供接口定义的生产环境。目录必须由部署系统创建并授予应用最小读取权限。

需要使用编辑器修改文件时，必须显式改为可写并配置认证：

```yaml
simple-secret:
  magic-api:
    enabled: true

magic-api:
  web: /magic/web
  resource:
    type: file
    location: ${MAGIC_API_HOME:./data/magic-api}
    readonly: false
  security:
    username: ${MAGIC_API_USERNAME}
    password: ${MAGIC_API_PASSWORD}
```

只要 `magic-api.web` 非空，starter 就要求用户名和密码同时存在。只配置其中一项也会启动失败。生产环境还应通过反向代理提供 HTTPS、网络访问控制、审计和速率限制；不要把编辑器直接暴露到公网。

## 数据库资源

数据库模式不要求文件目录，但宿主应用必须提供可用的 `DataSource`。通常由应用显式引入并配置自己的 JDBC starter 和驱动：

```yaml
simple-secret:
  magic-api:
    enabled: true

magic-api:
  resource:
    type: database
    table-name: magic_api_file
  backup:
    enable: true
    table-name: magic_api_backup
    max-history: 30
```

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
```

未提供数据源时，上游 Magic API 会拒绝创建数据库资源。数据库账号应仅拥有 Magic API 所需表的最小权限，生产环境应启用备份并验证恢复流程。

## 自定义返回结构

starter 不强制依赖任何业务 Result 类型。声明 Magic API 原生 `ResultProvider` Bean 即可覆盖上游默认实现：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.ResultProvider;
import org.ssssssss.magicapi.modules.db.model.Page;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class MagicApiResultConfiguration {

    @Bean
    ResultProvider resultProvider() {
        return new ResultProvider() {
            @Override
            public Object buildResult(RequestEntity request, int code,
                                      String message, Object data) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("code", code);
                result.put("message", message);
                result.put("data", data);
                return result;
            }

            @Override
            public Object buildPageResult(RequestEntity request, Page page,
                                          long total,
                                          List<Map<String, Object>> data) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("page", page.getOffset());
                result.put("size", page.getLimit());
                result.put("total", total);
                result.put("records", data);
                return result;
            }
        };
    }
}
```

上游配置使用 `@ConditionalOnMissingBean(ResultProvider.class)`，因此消费者 Bean 会直接接管，不需要 `@Primary`。

## 显式风险选项

下列能力默认关闭，只应在明确理解暴露面后启用：

```yaml
magic-api:
  support-cross-domain: true
  show-sql: true
  show-url: true
  resource:
    readonly: false
```

- 跨域支持可能扩大编辑器或接口的浏览器访问范围，应配置精确的网关 CORS 策略。
- SQL 日志可能包含表名、条件值或业务数据，不应在生产环境长期启用。
- URL 输出会暴露应用路由信息。
- 可写资源允许通过 Magic API 修改接口定义，应配合认证、备份和变更审计。
- `magic-api.secret-key` 会启用远程推送入口，密钥必须由密钥系统注入并定期轮换。

## 可选插件

核心 starter 不传递以下依赖：

- `org.ssssssss:magic-api-plugin-task`
- `org.ssssssss:magic-api-plugin-springdoc`

确实需要时由应用自行显式声明，并独立评估其 Spring Boot 3.5 兼容性、传递依赖和安全暴露面。Honeybee 的定制 springdoc 注册逻辑依赖其私有 doc 配置，不属于本模块。

## 完整应用配置

以下配置启用只读文件资源，不暴露编辑器，也不输出 SQL 或地址：

```yaml
spring:
  application:
    name: magic-api-service

simple-secret:
  magic-api:
    enabled: true

magic-api:
  prefix: /api
  resource:
    type: file
    location: ${MAGIC_API_HOME:/opt/magic-api}
    readonly: true
  support-cross-domain: false
  show-sql: false
  show-url: false
  banner: false
  page:
    default-page: 1
    default-size: 20
    max-page-size: 500
```

部署前确认资源目录存在、接口文件来源可信、应用路由已受认证授权控制，并根据上游 Magic API 的执行模型限制可导入包、数据源权限和外部 HTTP 访问能力。

# Simple Secret Doc Starter

`simple-secret-springboot-starter-doc` 为 Java 17、Spring Boot 3.5 WebMVC 应用提供受控的 OpenAPI 配置。模块基于 springdoc 2.8.17，只使用公开的 `OpenAPI` 和 `OperationCustomizer` 扩展点，不替换 springdoc 内部 service。

starter 默认关闭，不公开 `/v3/api-docs`，也不传递 Swagger UI。它不依赖 Knife4j、Therapi、Jackson Kotlin、Simple Secret JSON/core/toolbox、Lombok 或 Honeybee 私有模块。

## Maven 依赖

WebMVC 应用先声明自己的 Web starter，再按需添加 doc starter：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-doc</artifactId>
</dependency>
```

以上示例假定已经导入 `simple-secret-common-bom`。未使用 BOM 时为 doc starter 指定 `<version>1.1.0</version>`。

doc starter 不传递 Servlet 容器，因为应用可能使用 Tomcat、Jetty 或 Undertow。`spring-boot-starter-web` 只是最常见的 WebMVC 前置依赖。

## 默认行为

仅添加依赖时，starter 提供最低优先级安全默认值：

```yaml
simple-secret:
  doc:
    enabled: false

springdoc:
  api-docs:
    enabled: false
```

应用配置、环境变量和命令行参数优先于该默认值。设置 `simple-secret.doc.enabled=true` 会默认同步开启 springdoc API docs；如果应用显式设置 `springdoc.api-docs.enabled=false`，则仍保持关闭。

## 基础文档信息

```yaml
simple-secret:
  doc:
    enabled: true
    info:
      title: ${spring.application.name} API
      description: Order service endpoints
      version: 1.0.0
      terms-of-service: https://example.com/terms
      contact:
        name: API Team
        email: api@example.com
        url: https://example.com/team
      license:
        name: Apache-2.0
        url: https://www.apache.org/licenses/LICENSE-2.0
```

默认 OpenAPI JSON 地址由 springdoc 提供，通常为 `/v3/api-docs`。starter 不修改 servlet context path，也不会再次给生成的 paths 拼接前缀。

## API Key 鉴权

`API_KEY` 支持 `HEADER`、`QUERY` 和 `COOKIE` 位置。以下配置声明请求头 API Key，并将它应用到所有 Operation：

```yaml
simple-secret:
  doc:
    enabled: true
    security:
      schemes:
        apiKey:
          type: API_KEY
          location: HEADER
          parameter-name: X-API-Key
          description: Gateway API key
      globally-required:
        - apiKey
```

方案名称不能为空，API Key 的 `parameter-name` 和 `location` 必须有效。错误配置会在应用启动阶段明确失败。

## HTTP Basic 和 Bearer

```yaml
simple-secret:
  doc:
    enabled: true
    security:
      schemes:
        basicAuth:
          type: HTTP_BASIC
          description: Operations console credentials
        bearerAuth:
          type: HTTP_BEARER
          bearer-format: JWT
          description: OAuth access token
      globally-required:
        - bearerAuth
```

支持的类型固定为 `API_KEY`、`HTTP_BASIC` 和 `HTTP_BEARER`。`globally-required` 只能引用 `schemes` 中已经声明的名称，并且只会把列出的方案应用到顶层 SecurityRequirement。

OAuth2 flows、OpenID Connect、多租户动态 scheme 或更复杂的 Operation 级组合不在 starter 的简化配置范围内。此类场景由应用声明自己的 `OpenAPI` Bean：

```java
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApplicationOpenApiConfiguration {

    @Bean
    OpenAPI applicationOpenApi() {
        SecurityScheme oauth = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                        .authorizationUrl("https://identity.example.com/oauth/authorize")
                        .tokenUrl("https://identity.example.com/oauth/token")));
        return new OpenAPI()
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("oauth", oauth));
    }
}
```

消费者 `OpenAPI` Bean 存在时 starter 完全让位。

## 分组配置

分组直接使用 springdoc 原生配置，不在 starter 中重复建模：

```yaml
springdoc:
  group-configs:
    - group: public-api
      packages-to-scan: com.example.orders.api
    - group: internal-api
      packages-to-scan: com.example.orders.internal
      paths-to-match: /internal/**
```

多组文档的地址和其他行为以 springdoc 2.8 文档为准。

## Swagger UI

starter 只传递 WebMVC API，不传递 Swagger UI。需要官方 UI 时由应用显式添加，Simple Secret BOM 已管理相同的 springdoc 2.8.17 版本：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

```yaml
springdoc:
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    persist-authorization: false
```

Knife4j 也是消费者可选 UI，但本项目不管理或传递其版本。引入前必须确认所选 Knife4j 版本与 Spring Boot 3.5、springdoc 2.8 和 Jakarta Servlet 兼容，并独立扫描其传递依赖；不要同时无目的地引入多个 UI starter。

## 可选 Javadoc 标签

默认不从源码 Javadoc 生成标签：

```yaml
simple-secret:
  doc:
    enabled: true
    javadoc-tags-enabled: false
```

需要该能力时，应用显式引入 Therapi runtime，并在编译器中配置 scribe 注解处理器：

```xml
<dependency>
    <groupId>com.github.therapi</groupId>
    <artifactId>therapi-runtime-javadoc</artifactId>
    <version>0.15.0</version>
</dependency>
```

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>com.github.therapi</groupId>
                <artifactId>therapi-runtime-javadoc-scribe</artifactId>
                <version>0.15.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

```yaml
simple-secret:
  doc:
    enabled: true
    javadoc-tags-enabled: true
```

存在 springdoc `JavadocProvider` 时，starter 使用控制器类 Javadoc 的第一条非空文本作为 Operation tag。没有 provider、Javadoc 为空或标签已经存在时不会修改 Operation。更稳定的公开 API 建议直接使用 Swagger `@Tag` 和 `@Operation` 注解。

## 生产环境

OpenAPI 文档会暴露路由、参数、模型和部分鉴权设计。生产环境应采用以下一种策略：

- 保持 `simple-secret.doc.enabled=false`。
- 只在内网或运维网络开放文档地址。
- 通过 Spring Security、API 网关或反向代理对 `/v3/api-docs/**` 和 UI 地址进行认证授权。
- 禁止把真实 token、密码、内部主机名或敏感示例值写入 description、example 和默认值。
- UI 使用 `persist-authorization: false`，避免浏览器长期保存凭据。

只隐藏 UI 不能保护 OpenAPI JSON；必须同时控制 `/v3/api-docs` 访问。

## 从 Honeybee 迁移

- 配置前缀从 `springdoc.info` 改为 `simple-secret.doc.info`。
- 不再加载 `honeybee-doc.yml`，没有固定作者、版权或默认 Authorization scheme。
- 默认关闭 API docs，不再默认开启 Knife4j。
- 不再覆盖 `OpenAPIService`，springdoc 升级边界更稳定。
- 不再手动给 paths 拼接 servlet context path。
- Javadoc 标签需要显式开启，Therapi 由消费者按需引入。
- 高级 Components、Paths、Tags 或 OAuth2 配置通过消费者 `OpenAPI` Bean 或 springdoc 原生扩展点完成。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-doc \
  test
```

独立消费者测试位于 `integration-tests/consumer-doc`，验证 BOM 接入、默认关闭、显式启用和消费者 Bean 覆盖。

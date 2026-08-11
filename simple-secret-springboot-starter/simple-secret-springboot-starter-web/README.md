# Simple Secret WebMVC Starter

`simple-secret-springboot-starter-web` 为 Spring Boot 3.5 WebMVC 应用提供可选的 `BaseController`、安全的默认异常响应、CORS 配置和低风险请求耗时日志。它不传递 Web 服务器或 `spring-boot-starter-web`；应用自行选择 WebMVC 运行时并显式启用每项功能。

## Maven 依赖

先导入 Simple Secret BOM，再由应用显式选择 Spring Boot Web starter。Simple Secret BOM 既管理 Simple Secret 模块版本，也锁定项目统一采用的 Jackson、springdoc、POI、Commons 等第三方版本；同一 `dependencyManagement` 中必须把它放在 Spring Boot BOM 前面，使这些约束优先生效。Spring Boot 版本仍由消费者选择：

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
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.5.16</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.ss</groupId>
        <artifactId>simple-secret-springboot-starter-web</artifactId>
    </dependency>
</dependencies>
```

未使用 BOM 时，才为 `simple-secret-springboot-starter-web` 显式指定版本。需要 Bean Validation 的应用自行引入 `spring-boot-starter-validation`。

## 默认关闭

只引入依赖时不会注册本 starter 的异常 advice、CORS source、CORS MVC 映射或请求耗时拦截器：

```yaml
simple-secret:
  web:
    enabled: false
    exception-handler:
      enabled: false
    cors:
      enabled: false
    request-timing:
      enabled: false
```

`simple-secret.web.enabled=true` 只允许自动配置参与条件判断；异常处理、CORS 和耗时记录仍须分别显式开启。这样应用可以仅使用 `BaseController`，或只启用其中一项 Web 行为。

## BaseController

`BaseController` 只包含结果转换与安全重定向辅助方法，不扫描 Controller，也不改写响应：

```java
import com.ss.core.domain.Result;
import com.ss.web.controller.BaseController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class OrderController extends BaseController {

    @PostMapping("/orders/close")
    Result<Void> closeOrder() {
        int updatedRows = 1;
        return toResult(updatedRows);
    }

    String backToOrders() {
        return redirect("/orders");
    }
}
```

`redirect` 拒绝空白地址和 CR/LF，避免响应头注入；应用仍应自行限制允许跳转的目标。

## 异常处理

启用后，starter 注册两个低优先级 `@RestControllerAdvice`。`SimpleSecretExceptionHandler` 处理 `ServiceException`、业务异常、常见绑定/请求异常和缺失资源；若 Validation API 存在，`SimpleSecretValidationExceptionHandler` 处理 `ConstraintViolationException`。客户端得到 `Result` JSON，不会收到异常 cause 或 `detailMessage`：

```yaml
simple-secret:
  web:
    enabled: true
    exception-handler:
      enabled: true
```

应用的 Controller advice 具有更高优先级时，可以按 Spring MVC 常规方式覆盖特定异常。需要完整接管默认处理器时，声明同类型 Bean，starter 会让位：

```java
import com.ss.web.error.SimpleSecretExceptionHandler;
import com.ss.web.error.SimpleSecretValidationExceptionHandler;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ErrorHandlingConfiguration {

    @Bean
    SimpleSecretExceptionHandler consumerExceptionHandler(MessageSource messageSource) {
        return SimpleSecretExceptionHandler.create(messageSource);
    }

    @Bean
    SimpleSecretValidationExceptionHandler consumerValidationExceptionHandler() {
        return SimpleSecretValidationExceptionHandler.create();
    }
}
```

第二个 Bean 仅在应用引入 Jakarta Validation 后有意义。若要变更响应结构，应用应声明自己的 `@RestControllerAdvice`，而不是向客户端返回 cause、堆栈或内部诊断字段。

## 安全 CORS

以下示例只允许一个明确的 HTTPS 前端来源携带凭据。不要与凭据一起使用 wildcard origin 或 wildcard origin pattern：

```yaml
simple-secret:
  web:
    enabled: true
    cors:
      enabled: true
      path: /api/**
      allowed-origins:
        - https://app.example.com
      allowed-methods:
        - GET
        - POST
      allowed-headers:
        - Authorization
        - Content-Type
      exposed-headers:
        - X-Request-Id
      allow-credentials: true
      max-age: 30m
```

启用 CORS 时必须至少配置一个来源或来源模式；空白值、负 `max-age` 和凭据配合 wildcard 都会在启动时拒绝。`simpleSecretCorsConfigurationSource` 同时可供 Spring Security 复用，并由 `WebCorsWebMvcConfigurer` 应用于纯 WebMVC。

应用可提供自己的 `CorsConfigurationSource` 来接管 source，starter 会同时放弃它自己的 source 和 CORS MVC configurer；此时应用负责把 source 接入 Spring Security 或声明自己的 MVC CORS 映射：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class ConsumerCorsConfiguration {

    @Bean
    CorsConfigurationSource consumerCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://app.example.com"));
        configuration.setAllowedMethods(List.of("GET", "POST"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

`HandlerMappingIntrospector` 是 Spring MVC 的框架基础设施，虽然它实现了 CORS 相关接口，但不是消费者显式提供的 CORS source；starter 会忽略该基础设施 Bean，继续按上述属性创建自己的 source。

## 请求耗时

耗时拦截器仅记录 method、已匹配路由模板、HTTP status 和耗时，不读取 URI、查询参数、请求头、Cookie、参数、请求体或异常信息：

```yaml
simple-secret:
  web:
    enabled: true
    request-timing:
      enabled: true
      slow-request-threshold: 1s

logging:
  level:
    com.ss.web.observability: DEBUG
```

达到阈值时记录 WARN，否则在 DEBUG 开启时记录 DEBUG。应用可以按公开类型替换拦截器或整个 MVC configurer：

```java
import com.ss.web.observability.RequestTimingInterceptor;
import com.ss.web.observability.RequestTimingWebMvcConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class TimingConfiguration {

    @Bean
    RequestTimingInterceptor consumerRequestTimingInterceptor() {
        return new RequestTimingInterceptor(Duration.ofMillis(500));
    }

    @Bean
    RequestTimingWebMvcConfigurer consumerRequestTimingWebMvcConfigurer(
            RequestTimingInterceptor consumerRequestTimingInterceptor) {
        return new RequestTimingWebMvcConfigurer(consumerRequestTimingInterceptor);
    }
}
```

## 依赖与安全边界

生产依赖只有 `simple-secret-common-core`、`spring-boot-autoconfigure`、`spring-webmvc`、provided 的 Servlet API 和 optional 的 Jakarta Validation API。它不传递 `spring-boot-starter-web`、Tomcat/Jetty/Undertow、Validation 实现、JSON starter、Spring Security、Actuator、Swagger/Knife4j、Hutool 或 Honeybee 私有模块。

本 starter 不是完整 Web 安全方案。XSS 防护依赖上下文输出编码、模板转义、Content Security Policy (CSP) 和输入校验；不要把这类职责交给一个全局请求包装器。认证授权、CSRF、限流、审计、文件上传策略和安全响应头由应用或网关负责。

## 不迁移

以下 Honeybee 风格能力不在本模块迁移范围内：

- 全局 XSS Filter、请求包装器和任意 HTML 清洗策略。
- 认证、授权、Token、数据权限、租户、验证码与 CSRF 策略。
- Swagger/Knife4j、全局 Jackson/日期格式、分页、文件上传和下载策略。
- 全局 CORS wildcard 默认值、异常堆栈/内部详情输出，以及 Web 容器或线程池配置。

这些能力要么属于特定业务和安全边界，要么已有独立 starter，必须由消费者基于自身风险模型显式选择。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-web \
  test

JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -f integration-tests/pom.xml -pl consumer-web test
```

独立 consumer 使用真实 `SpringApplication` 和随机端口 GET/OPTIONS 请求，验证默认无副作用、显式异常/CORS/timing 行为、异常响应不泄漏内部信息，以及消费者 Bean 覆盖。

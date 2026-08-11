# Simple Secret Security Starter

`simple-secret-springboot-starter-security` 为 Spring WebMVC 路由提供最小登录保护。它只注册一个调用 `StpLogic.checkLogin()` 的拦截器，不实现用户、权限、角色、客户端、租户或 Token 存储。

## BOM 与依赖

先导入 Simple Secret BOM。若同时导入 Spring Boot BOM，Simple Secret BOM 放在前面：

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
```

Security starter 不传递 Web 容器、官方 Sa-Token Spring Boot starter 或 Auth starter。Servlet 应用需要显式声明：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-spring-boot3-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.ss</groupId>
        <artifactId>simple-secret-springboot-starter-security</artifactId>
    </dependency>
</dependencies>
```

以上依赖均由 BOM 管理版本。Security starter 自身的生产依赖仅包含 Sa-Token core、Spring 自动配置与 WebMVC 编译契约，不传递 Servlet 容器。

## 路由登录保护

默认不注册 `SecurityProperties`、`StpLogic`、`LoginRequiredInterceptor` 或 `SecurityWebMvcConfigurer`。显式开启并配置受保护路径：

```yaml
simple-secret:
  security:
    enabled: true
    path-patterns:
      - /api/**
      - /management/**
    exclude-path-patterns:
      - /api/public/**
      - /management/health
    order: 0
```

默认值如下：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `simple-secret.security.enabled` | `false` | 是否注册路由登录保护 |
| `simple-secret.security.path-patterns` | `/**` | 需要执行 `checkLogin()` 的路径 |
| `simple-secret.security.exclude-path-patterns` | 空列表 | 从受保护路径中排除的路径 |
| `simple-secret.security.order` | `0` | WebMVC 拦截器顺序 |

路径值必须非空、非空白且不能带首尾空格。显式空 `path-patterns` 表示当前不匹配任何路径，starter 不会注册登录拦截器；`enabled: false` 则会关闭全部 Security 运行时 Bean。

本模块没有内置匿名白名单。Swagger、OpenAPI、Actuator、静态资源、登录端点和 `/error` 都不会自动排除；应用必须根据自己的公开面显式配置 `exclude-path-patterns`。排除路径只跳过本拦截器，不等于完成生产访问控制。

## 与 Auth Starter 组合

Security starter 不处理 `NotLoginException`。应用没有异常处理器时，异常保持原样交给现有 WebMVC 异常链。需要 Simple Secret 的固定 401 JSON 时，显式加入 Auth starter：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-auth</artifactId>
</dependency>
```

同时开启 Auth core、Auth advice 和 Security：

```yaml
simple-secret:
  auth:
    enabled: true
    exception-handler:
      enabled: true
  security:
    enabled: true
    path-patterns:
      - /api/**
    exclude-path-patterns:
      - /api/login
```

未登录请求会得到 HTTP 401 和固定消息 `认证失败，无法访问系统资源`。响应不会包含 Sa-Token 原始异常、URI、Token 或 loginType。Auth starter 不是 Security starter 的传递依赖，应用必须显式声明才能使用该 advice。

## 覆盖默认 Bean

消费者提供同类型 Bean 时，starter 会逐项回退。可以只替换 `StpLogic`，也可以接管拦截器或完整路径注册：

```java
import cn.dev33.satoken.stp.StpLogic;
import com.ss.security.config.SecurityProperties;
import com.ss.security.web.LoginRequiredInterceptor;
import com.ss.security.web.SecurityWebMvcConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SecurityOverrides {

    @Bean
    StpLogic applicationStpLogic() {
        return new StpLogic("application");
    }

    @Bean
    LoginRequiredInterceptor applicationLoginRequiredInterceptor(StpLogic applicationStpLogic) {
        return new LoginRequiredInterceptor(applicationStpLogic);
    }

    @Bean
    SecurityWebMvcConfigurer applicationSecurityWebMvcConfigurer(
            LoginRequiredInterceptor interceptor, SecurityProperties properties) {
        return new SecurityWebMvcConfigurer(interceptor, properties);
    }
}
```

替换 `SecurityWebMvcConfigurer` 后，include、exclude 和 order 的解释完全由应用负责。替换 `LoginRequiredInterceptor` 后，应用还需自行保证异常传播和敏感信息处理符合安全要求。

## 安全与功能边界

拦截器不读取 URI、header、query、parameter、body、cookie、Token、登录 ID、客户端 ID 或 token extra，也不记录这些数据。它只调用绑定 `StpLogic` 的 `checkLogin()` 并传播异常。

以下能力不属于本模块：

- 动态扫描控制器 URL 或根据注解生成白名单。
- 权限码、角色、数据权限、租户、客户端 ID 或 token extra 校验。
- CSRF、CORS、限流、验证码、账号锁定或 Spring Security 过滤器链。
- Redis、Redisson、JWT、分布式会话存储和 Token 密钥管理。
- Swagger、Actuator、静态资源、登录接口或 `/error` 的默认放行规则。

生产环境应显式审查所有 include/exclude 路径，并在应用或网关层完成 HTTPS、CSRF、CORS、速率限制和审计。日志、指标标签、异常响应与调试输出中不得写入 Token、登录 ID、权限码、角色码、客户端 ID 或请求参数。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-security clean verify

JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -DskipTests install

JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -f integration-tests/pom.xml -pl consumer-security test
```

消费者测试验证 BOM 无版本依赖、默认关闭、显式路径保护、异常原样传播、Auth 固定 401 响应和全部默认 Bean 回退。

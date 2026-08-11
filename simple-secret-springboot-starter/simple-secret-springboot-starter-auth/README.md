# Simple Secret Auth Starter

`simple-secret-springboot-starter-auth` 提供基于 Sa-Token 的登录用户模型、登录策略分派、权限桥接和可选的 Servlet 认证异常响应。它只封装稳定的认证边界，不包含用户、客户端、租户或权限的业务实现。

## BOM 与依赖

先导入 Simple Secret BOM。它管理 Simple Secret 模块和 Sa-Token 的版本；若同时使用 Spring Boot BOM，Simple Secret BOM 放在前面：

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

### 非 Web 场景

非 Web 应用可直接使用认证领域类型和策略接口；只需要下面的依赖，不会引入 Servlet 容器或官方 Sa-Token Spring Boot starter：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-auth</artifactId>
</dependency>
```

例如，应用可以实现一个认证策略并在自己的认证入口中调用它：

```java
import com.ss.auth.domain.BaseClientDomain;
import com.ss.auth.domain.BaseLoginBody;
import com.ss.auth.domain.LoginUser;
import com.ss.auth.service.AuthStrategy;

import java.util.Map;
import java.util.Set;

class PasswordAuthStrategy implements AuthStrategy {

    @Override
    public String grantType() {
        return "password";
    }

    @Override
    public LoginUser login(BaseLoginBody body, BaseClientDomain client) {
        return new LoginUser("application-subject", "application-user", Set.of(), Set.of(), Map.of());
    }
}
```

这段代码只构造登录结果，不应把它写入日志或返回给不可信调用方。实际校验密码、客户端密钥和用户状态始终由应用负责。

### Servlet Web 场景

Auth starter 不传递 Web 容器或官方 Sa-Token Spring Boot starter。Servlet 应用必须显式声明两者，再添加 Auth starter：

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
        <artifactId>simple-secret-springboot-starter-auth</artifactId>
    </dependency>
</dependencies>
```

上例全部由 BOM 管理版本，因此 Auth starter 不写 `<version>`。应用选择 Tomcat、Jetty 或 Undertow 的方式仍由 `spring-boot-starter-web` 或其替代 starter 决定。

## 配置边界与默认行为

只引入 Auth starter 时不会注册 `LoginHelper`、`StpInterface`、`AuthStrategyRegistry` 或 Servlet advice。主开关和异常处理开关均默认关闭：

```yaml
simple-secret:
  auth:
    enabled: false
    exception-handler:
      enabled: false
```

`simple-secret.auth.*` 只控制本 starter 的自动配置。Sa-Token 自己的运行时、Token 名称、超时、存储和路由规则由原生 `sa-token.*` 管理，例如：

```yaml
simple-secret:
  auth:
    enabled: true
    exception-handler:
      enabled: true

sa-token:
  token-name: X-Access-Token
  timeout: 1800
```

不要把 `sa-token.*` 配置放到 `simple-secret.auth.*` 下，也不要期待本 starter 覆盖官方 Sa-Token 的存储或路由配置。

## 登录用户与会话

主开关开启时，Auth starter 会基于 Sa-Token core 创建 `StpLogic` 和 `LoginHelper`。Servlet 请求中使用 token 上下文时，应用仍需显式引入官方 Sa-Token starter。认证成功后，将应用已经验证过的 `LoginUser` 写入当前 token 会话：

```java
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.ss.auth.domain.LoginUser;
import com.ss.auth.support.LoginHelper;

import java.util.Map;
import java.util.Set;

class LoginApplicationService {
    private final LoginHelper loginHelper;

    LoginApplicationService(LoginHelper loginHelper) {
        this.loginHelper = loginHelper;
    }

    void completeLogin() {
        LoginUser loginUser = new LoginUser(
                "application-subject", "application-user", Set.of(), Set.of(), Map.of());
        loginHelper.login(loginUser, new SaLoginParameter());
    }
}
```

`LoginHelper.requireLoginUser()` 在当前会话没有可用登录用户时抛出固定 `AuthException`。应用响应 DTO 应仅返回业务所需字段；不要把 Token、登录主体标识、权限集合或角色集合写到日志、诊断输出或公开响应中。

## 策略、客户端与注册表

应用提供 `ClientService` Bean 后，starter 注册 `AuthStrategyRegistry`；可用策略来自当前所有 `AuthStrategy` Bean。没有策略时 registry 仍可创建，但任何授权类型都会被拒绝。注册表精确匹配 `grantType`，并在分派策略前校验客户端存在、状态为 `NORMAL` 且允许该授权类型：

```java
import com.ss.auth.domain.BaseClientDomain;
import com.ss.auth.domain.BaseLoginBody;
import com.ss.auth.domain.ClientStatus;
import com.ss.auth.domain.LoginUser;
import com.ss.auth.service.AuthStrategy;
import com.ss.auth.service.ClientService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
class AuthenticationConfiguration {

    @Bean
    ClientService clientService() {
        return clientId -> {
            BaseClientDomain client = new BaseClientDomain();
            client.setClientId(clientId);
            client.setStatus(ClientStatus.NORMAL);
            client.setGrantTypeList(List.of("password"));
            return client;
        };
    }

    @Bean
    AuthStrategy passwordAuthStrategy() {
        return new AuthStrategy() {
            @Override
            public String grantType() {
                return "password";
            }

            @Override
            public LoginUser login(BaseLoginBody body, BaseClientDomain client) {
                return new LoginUser(
                        "application-subject", "application-user", Set.of(), Set.of(), Map.of());
            }
        };
    }
}
```

策略必须在调用 `LoginHelper.login(...)` 前完成其对应的凭据和账户校验。重复、空白或带首尾空格的 `grantType` 会在注册或请求处理阶段被拒绝。

## 覆盖默认 Bean

消费者可声明同类型 Bean 接管 starter 默认值。以下示例同时替换 Sa-Token 逻辑、会话辅助、权限桥接和策略注册表；starter 会对每一个默认 Bean 回退：

```java
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import com.ss.auth.service.AuthStrategy;
import com.ss.auth.service.ClientService;
import com.ss.auth.strategy.AuthStrategyRegistry;
import com.ss.auth.support.LoginHelper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
class AuthOverrides {

    @Bean
    StpLogic applicationStpLogic() {
        return new StpLogic("application");
    }

    @Bean
    LoginHelper applicationLoginHelper(StpLogic applicationStpLogic) {
        return new LoginHelper(applicationStpLogic);
    }

    @Bean
    StpInterface applicationStpInterface() {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return List.of();
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return List.of();
            }
        };
    }

    @Bean
    AuthStrategyRegistry applicationAuthStrategyRegistry(
            ClientService clientService, List<AuthStrategy> strategies) {
        return new AuthStrategyRegistry(clientService, strategies);
    }
}
```

Servlet exception advice 也可替换：

```java
import com.ss.auth.web.SimpleSecretAuthExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuthErrorHandlingConfiguration {

    @Bean
    SimpleSecretAuthExceptionHandler applicationAuthExceptionHandler() {
        return SimpleSecretAuthExceptionHandler.create();
    }
}
```

这只替换默认 advice；如需完全不同的公开响应模型，应用可声明自己的 `@RestControllerAdvice`。不要将异常 message、cause、堆栈、Token、登录主体标识、权限或角色数据写入响应。

## 可选 Servlet 异常响应

将两个开关都设为 `true` 后，starter 注册最低优先级的 `@RestControllerAdvice`。固定 JSON 使用 `Result`，不包含 Sa-Token 原始异常消息或认证上下文：

| 异常 | HTTP 状态 | 固定消息 |
| --- | --- | --- |
| `NotLoginException` | 401 | 认证失败，无法访问系统资源 |
| `NotPermissionException` | 403 | 没有访问权限 |
| `NotRoleException` | 403 | 没有访问权限 |
| `AuthException(INVALID_REQUEST)` | 400 | 认证请求无效 |
| `AuthException(UNSUPPORTED_GRANT)` | 400 | 认证方式不受支持 |
| `AuthException(CLIENT_UNAVAILABLE)` | 401 | 认证失败 |
| `AuthException(UNAUTHENTICATED)` | 401 | 认证失败 |

应用的更高优先级 advice 可处理自己的业务异常。认证失败事件需要审计时，只记录经过脱敏和最小化后的事件类型、请求关联 ID 与结果码。

## 不迁移项与安全边界

以下能力不属于 Auth starter：

- Redis、Redisson、分布式 Sa-Token DAO 或其他外部会话存储；应用按部署拓扑单独选择和配置。
- JWT 插件、JWT 密钥、签发策略和密钥轮换；需要时由应用显式引入兼容的 Sa-Token JWT 依赖并自行管理密钥。
- Bean Validation starter、登录请求字段校验和验证码实现；应用显式引入 Validation 并实现自己的校验规则。
- 用户、客户端、租户、数据权限、账号锁定、密码策略、验证码、第三方身份提供商、网关路由和 Spring Security 集成。

严禁在日志、指标标签、异常消息、调试输出或公开响应中打印 Token、登录主体标识、权限码、角色、客户端密钥、密码、验证码或原始认证异常。生产环境还应使用 HTTPS、限制认证端点速率，并把审计事件交给应用的安全治理流程。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-auth clean verify

JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn -DskipTests install

JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -f integration-tests/pom.xml -pl consumer-auth test
```

消费者测试使用真实随机端口 HTTP 请求，验证 BOM 无版本消费、默认关闭、策略注册、全部默认 Bean 回退，以及固定认证失败响应不泄漏认证上下文。

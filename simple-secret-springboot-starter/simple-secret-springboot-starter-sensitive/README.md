# Simple Secret Sensitive Starter

`simple-secret-springboot-starter-sensitive` 为 Jackson JSON 输出提供字段级数据脱敏。模块面向 Java 17、
Spring Boot 3.5 和 Jackson 2.21，默认采用失败关闭行为：字段声明 `@Sensitive` 后，如果应用没有提供
决策 Bean，仍会执行脱敏，不会因为缺少认证或权限组件而输出明文。

## Maven 依赖

推荐先导入 Simple Secret BOM：

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
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.ss</groupId>
        <artifactId>simple-secret-springboot-starter-sensitive</artifactId>
    </dependency>
</dependencies>
```

模块不依赖 Simple Secret JSON starter、Hutool、Lombok、Auth、Security、Web、Redis 或 Tenant。
它只提供 Jackson module；应用仍需通过 Spring Boot JSON/Web starter 或自己的配置提供 ObjectMapper。

## 字段注解

```java
import com.ss.sensitive.annotation.Sensitive;
import com.ss.sensitive.core.SensitiveStrategy;

public class CustomerView {
    @Sensitive(strategy = SensitiveStrategy.PHONE)
    private String phone;

    @Sensitive(
            strategy = SensitiveStrategy.EMAIL,
            roleKey = "auditor",
            perms = "customer:read:raw")
    private String email;

    // getters and setters
}
```

内置策略包括 `ID_CARD`、`PHONE`、`ADDRESS`、`EMAIL` 和 `BANK_CARD`。注解只处理 String 字段；
其他字段即使误加注解也保持原有 Jackson 序列化行为。空字符串输出为空字符串，null 仍按 Jackson 的
null 规则输出。

## 授权决策

默认 `SensitiveService` 始终返回 true。应用只有在确认当前调用者可以查看原文时才返回 false：

```java
import com.ss.sensitive.core.SensitiveService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SensitivePolicyConfiguration {

    @Bean
    SensitiveService sensitiveService(CurrentAccess access) {
        return (roleKey, perms) -> !access.hasRole(roleKey)
                || !access.hasPermission(perms);
    }
}
```

`roleKey` 和 `perms` 只是注解传给业务策略的提示，本 starter 不解析角色、不依赖认证框架，也不内置
管理员白名单。决策服务抛出运行时异常时仍执行脱敏，避免授权组件故障导致明文泄露。

## 配置开关

自动配置默认启用，可显式关闭：

```yaml
simple-secret:
  sensitive:
    enabled: false
```

关闭后不会创建 `SensitiveService` 或 `SimpleSecretSensitiveModule`。消费者声明同类型 Bean 时，默认
Bean 会回退；Spring Boot 会把 sensitive module 与消费者的其他 Jackson Module 一起增量安装。

## 非 Spring 使用

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.sensitive.core.SensitiveService;
import com.ss.sensitive.jackson.SimpleSecretSensitiveModule;

ObjectMapper mapper = new ObjectMapper()
        .registerModule(new SimpleSecretSensitiveModule(SensitiveService.alwaysMask()));
```

## 安全边界

本模块只处理经过已注册 ObjectMapper 的 JSON 序列化。日志、数据库、消息 payload、自定义字符串拼接、
其他 ObjectMapper 和其他序列化框架不会自动脱敏。它不代替访问控制、数据库加密、TLS、日志治理或
数据最小化。业务 `SensitiveService` 返回 false 等同于允许输出原文，应进行独立代码审查和测试。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-sensitive \
  clean verify
```

模块测试覆盖五种策略、默认脱敏、消费者放行、非 String 字段、多个字段隔离、并发序列化、自动配置
开关、Bean 覆盖、消费者 Jackson module 共存和发布资源。

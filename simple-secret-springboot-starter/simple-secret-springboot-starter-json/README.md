# Simple Secret JSON Starter

`simple-secret-springboot-starter-json` 面向 Java 17 和 Spring Boot 3.5，提供独立 JSON 工具、可注入的 `JsonCodec`、JavaScript 数值精度保护、getter 属性名解析和 Jackson 空对象过滤。

模块遵循最小依赖原则，核心依赖只有 Jackson 和 `simple-secret-common-toolbox`。`spring-boot-autoconfigure` 与 `spring-web` 均为 optional，普通 Java 应用不会被强制传递 Spring Web。

## Maven 依赖

导入 Simple Secret BOM 后按需声明：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-json</artifactId>
</dependency>
```

未使用 BOM 时指定版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-json</artifactId>
    <version>1.1.0</version>
</dependency>
```

## 非 Spring 使用

`JsonUtils` 使用模块自己的默认 `ObjectMapper`，不会读取或修改 Spring 容器配置：

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.ss.json.utils.JsonUtils;

import java.util.List;
import java.util.Map;

String json = JsonUtils.toJsonString(new User(1L, "Alice"));
User user = JsonUtils.parseObject(json, User.class);

List<User> users = JsonUtils.parseArray(
        "[{\"id\":1,\"name\":\"Alice\"}]", User.class);
Map<String, Object> attributes = JsonUtils.parseMap("{\"enabled\":true}");
List<User> genericUsers = JsonUtils.parseObject(
        "[{\"id\":1,\"name\":\"Alice\"}]",
        new TypeReference<List<User>>() { });
```

空字符串解析对象时返回 `null`，解析数组时返回不可变空列表。解析或序列化失败统一抛出 `JsonOperationException`，异常消息不会回显原始 JSON 正文。

## Spring Boot 配置与使用

自动配置默认开启。业务代码应优先注入 `JsonCodec`；应用已有 `ObjectMapper` 时复用该 Bean，否则使用模块的独立默认 mapper：

```java
import com.ss.json.JsonCodec;
import org.springframework.stereotype.Service;

@Service
public class AuditPayloadService {
    private final JsonCodec jsonCodec;

    public AuditPayloadService(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public String encode(AuditEvent event) {
        return jsonCodec.toJsonString(event);
    }

    public AuditEvent decode(String payload) {
        return jsonCodec.parseObject(payload, AuditEvent.class);
    }
}
```

starter 默认不修改宿主应用的 `ObjectMapper`。需要把安全整数、`BigDecimal` 字符串格式、时区和序列化特性应用到宿主 mapper 时显式开启：

```yaml
simple-secret:
  json:
    enabled: true
    jackson-customization-enabled: true
```

设置 `simple-secret.json.enabled=false` 可关闭全部 JSON 自动配置。修改宿主 mapper 还要求 classpath 中存在 `Jackson2ObjectMapperBuilder`，通常由 `spring-boot-starter-web` 提供。非 Web Boot 环境没有 `ObjectMapper` Bean 时，starter 只发布 `JsonCodec`，不会额外发布 mapper Bean。

## 属性名解析

`JsonPropertyNameResolver` 可以从 getter 方法引用解析 JSON 属性名，并尊重 `@JsonProperty`：

```java
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ss.json.property.JsonPropertyNameResolver;
import com.ss.json.property.NameCase;

class User {
    @JsonProperty("display_name")
    private String userName;

    public String getUserName() {
        return userName;
    }
}

String property = JsonPropertyNameResolver.resolve(
        User::getUserName, "-", NameCase.LOWER);
// 结果为 display_name。
```

无法解析的 Lambda 会抛出明确异常，不会退化为脆弱的字符串猜测。

## 空对象过滤

`EmptyObjectFilter` 用于判断对象自身实例字段是否全部为空，可与 Jackson 自定义 include 配合：

```java
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ss.json.filter.EmptyObjectFilter;

class UserProfile {
    @JsonInclude(value = JsonInclude.Include.CUSTOM,
            valueFilter = EmptyObjectFilter.class)
    private Profile profile;
}
```

过滤器不把静态字段计入对象内容，也不会递归修改业务对象。

## 数值精度规则

默认 mapper 会将 JavaScript 安全整数范围 `[-9007199254740991, 9007199254740991]` 外的 `Long`、`BigInteger` 序列化为 JSON 字符串，并将 `BigDecimal` 序列化为字符串：

```json
{"safe":9007199254740991,"unsafe":"9007199254740992","amount":"12.3400"}
```

如果上下游协议要求所有数字都使用 JSON number，应保持 `jackson-customization-enabled=false`，并由宿主应用提供自己的 `ObjectMapper` 配置。

## 测试

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-json test
```

模块测试覆盖自动配置、依赖边界、静态工具、精度序列化、属性名解析和空对象过滤。

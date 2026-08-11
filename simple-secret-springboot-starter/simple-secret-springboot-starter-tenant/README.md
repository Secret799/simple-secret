# Simple Secret Tenant Starter

`simple-secret-springboot-starter-tenant` 面向 Java 17、Spring Boot 3.5 和
MyBatis-Plus 3.5.16，提供 SQL 行级租户隔离、显式排除表和可安全恢复的临时租户作用域。
缺失租户时采用 fail closed 行为：阻止需要隔离的 SQL，而不是注入 `NULL` 或跳过租户条件。

## Maven 依赖

推荐先导入 Simple Secret BOM，再声明 starter：

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
        <artifactId>simple-secret-springboot-starter-tenant</artifactId>
    </dependency>
</dependencies>
```

模块传递 MyBatis-Plus 基础 starter，并直接声明源码使用的 MyBatis、MyBatis-Plus、JSQLParser、
Spring Boot、Spring Context 和 Spring Core 契约。它不引入 Redis、Redisson、Sa-Token、Auth、
Web、JSON、Spring Cache、Toolbox、Hutool、Lombok 或 TransmittableThreadLocal。

## 配置

应用存在 `SqlSessionFactory` 时租户隔离默认启用：

```yaml
simple-secret:
  tenant:
    enabled: true
    column: tenant_id
    excluded-tables:
      - system_config
      - audit_log
```

`column` 只接受字母或下划线开头、后续包含字母数字或下划线的普通标识符。点号、空格、函数、
注释和其他 SQL 片段会在配置绑定时被拒绝。`excluded-tables` 采用忽略大小写的精确表名匹配，
starter 不内置 Honeybee 或任何业务系统的白名单。

设置 `simple-secret.tenant.enabled=false` 会关闭本 starter 的所有自动配置 Bean，但不会关闭
MyBatis-Plus 基础 starter。

## 提供当前租户

业务应用需要提供 `TenantContextProvider`，把自己的认证、请求或消息上下文映射为租户标识：

```java
import com.ss.tenant.context.TenantContextProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApplicationTenantConfiguration {

    @Bean
    TenantContextProvider tenantContextProvider(CurrentSession session) {
        return () -> session.isAuthenticated() ? session.tenantId() : null;
    }
}
```

provider 应只读取本地上下文，不应在每条 SQL 前调用远程服务。未提供 provider 时 starter 会注册
空 provider，使应用仍可启动；第一次执行非排除表 SQL 时会抛出 `TenantException`，防止无租户查询。

## 临时租户与忽略作用域

使用 `TenantContext` 的 callback API 可以保证异常时也恢复原上下文：

```java
import com.ss.tenant.context.TenantContext;

Order order = tenantContext.callWithTenant(
        "tenant-a", () -> orderMapper.selectById(orderId));

tenantContext.runWithoutTenant(() ->
        migrationMapper.rebuildTenantSummary());
```

需要跨多个语句时使用 try-with-resources：

```java
import com.ss.tenant.context.TenantScope;

try (TenantScope ignored = tenantContext.useTenant("tenant-b")) {
    orderService.recalculate();
    invoiceService.recalculate();
}
```

作用域支持嵌套并严格按后进先出恢复。它使用普通 `ThreadLocal`，不会隐式传播到线程池、异步任务
或消息消费线程；目标线程必须显式调用 `useTenant`、`runWithTenant` 或 `callWithTenant`。
`runWithoutTenant` 和 `ignoreTenant` 会绕过 SQL 行级隔离，只应放在经过授权、范围固定的管理流程中。

## 租户实体

实体可继承 `TenantEntity` 获得审计字段和 `tenantId`：

```java
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ss.tenant.domain.TenantEntity;

@TableName("orders")
public class OrderEntity extends TenantEntity {
    @TableId
    private Long id;
    private String orderNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}
```

租户插件按 SQL 表和列工作，并不要求实体必须继承 `TenantEntity`。已有实体只要映射同一租户列即可。
`TenantEntity.tenantId` 被标记为不参与 MyBatis-Plus 自动生成的 INSERT 和 UPDATE，租户值统一由
SQL 插件注入。setter 仅用于承接查询结果，不应作为写入其他租户的入口。

## 写入保护

启用隔离的表遵循以下失败关闭规则：

- 普通 INSERT 必须声明列清单，且不能显式包含租户列；插件会注入当前租户。
- `INSERT INTO table VALUES (...)` 因无法安全识别列顺序而被拒绝。
- 普通租户作用域内的 UPDATE 不能修改租户列，同时仍会追加当前租户 WHERE 条件。
- DELETE、UPDATE 和 SELECT 都会限制在当前租户；缺失租户时抛出 `TenantException`。
- 只有显式 `ignoreTenant()` / `runWithoutTenant()` 作用域允许受信任的迁移代码修改租户列。

因此业务 Mapper 不要把 `tenant_id` 写入 INSERT/UPDATE SQL。需要跨租户迁移时，应在完成独立授权
和范围校验后使用忽略作用域，并避免把该能力暴露给普通请求参数。

## 覆盖默认 Bean

`TenantContextProvider`、`TenantContext`、`TenantLineHandler`、`TenantLineInnerInterceptor` 和
`TenantMybatisPlusInterceptorCustomizer` 均支持消费者覆盖。若应用声明自己的
`MybatisPlusInterceptor`，MyBatis-Plus 基础 starter 会完全回退，tenant starter 不会后置修改该 Bean。
应用必须自行把 Simple Secret 严格租户拦截器放在分页等 SQL 改写插件之前：

```java
@Bean
TenantLineInnerInterceptor applicationTenantInterceptor(TenantLineHandler handler) {
    return new SimpleSecretTenantLineInnerInterceptor(handler);
}

@Bean
MybatisPlusInterceptor applicationInterceptor(TenantLineInnerInterceptor tenantInterceptor) {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(tenantInterceptor);
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
    return interceptor;
}
```

tenant starter 会在所有单例初始化完成后验证最终插件链：应用必须存在且只存在一个
`MybatisPlusInterceptor`，其中必须恰好包含当前 `SimpleSecretTenantLineInnerInterceptor`，并且 tenant
插件必须早于分页和乐观锁。设置 `simple-secret.mybatis.enabled=false` 且不提供完整替代容器、使用
没有写保护的标准 `TenantLineInnerInterceptor`、漏装 tenant 插件或顺序错误都会直接导致应用启动
失败，避免配置错误变成无租户 SQL。

## 安全边界

本 starter 只拦截经过同一 MyBatis-Plus 插件链执行的 SQL。直接使用 raw JDBC、`JdbcTemplate`、JPA
或其他 ORM 不会自动获得租户保护；这些路径必须由应用自行隔离。MyBatis-Plus
`@InterceptorIgnore(tenantLine = "true")` 同样会绕过租户插件，只能用于经过代码审查的可信基础设施
语句。生产环境仍建议配合数据库权限、约束或数据库原生行级安全，避免单层防护失效。

## 未迁移能力

Honeybee 原 tenant 模块中的 Redis/Redisson key 前缀、Spring CacheManager、Sa-Token DAO、
LoginHelper、用户级全局动态租户和 TransmittableThreadLocal 没有进入本 starter。这些能力会扩大
依赖并混合 SQL、缓存和认证边界，如需使用应由独立 opt-in 模块实现。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-tenant \
  clean verify
```

模块测试覆盖缺失租户失败关闭、嵌套作用域恢复、列名校验、排除表、插件顺序、写入保护、消费者
Bean 覆盖、最小依赖和发布资源。独立消费者测试额外使用 H2 验证真实 SELECT、INSERT 和 UPDATE
租户隔离。

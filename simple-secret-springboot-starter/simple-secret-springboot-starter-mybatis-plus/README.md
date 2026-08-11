# Simple Secret MyBatis-Plus Starter

`simple-secret-springboot-starter-mybatis-plus` 面向 Java 17、Spring Boot 3.5 和
MyBatis-Plus 3.5.16，提供安全分页、审计字段、字段状态缓存、基础 Mapper、数据库类型识别，以及分页与乐观锁
自动配置。

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
        <artifactId>simple-secret-springboot-starter-mybatis-plus</artifactId>
    </dependency>
</dependencies>
```

starter 直接声明它公开 API 实际使用的 MyBatis、MyBatis-Plus core/annotation/extension、
JSQLParser、Spring Boot、Spring Context 和 Spring Beans 契约，并保留 MyBatis-Plus Boot 3 starter 负责完整
Boot/JDBC 集成。模块复用 `simple-secret-common-toolbox` 的 JDK-only 过期缓存，不引入
dynamic-datasource、MP Join、P6Spy、Auth、Web、JSON、Hutool 或 Lombok。

## 配置

当应用已有 `SqlSessionFactory` 时，增强功能默认启用：

```yaml
simple-secret:
  mybatis:
    enabled: true
    pagination-enabled: true
    optimistic-locker-enabled: true
    max-page-size: 500
    overflow: false
```

`max-page-size` 必须大于零。`overflow=false` 表示页码越界时不自动跳回第一页，避免调用方把
错误页码误认为有效结果。设置 `simple-secret.mybatis.enabled=false` 会关闭本 starter 的全部
增强 Bean，但不会关闭 MyBatis-Plus 官方 starter。

## 基础实体

实体可按需继承 `BaseEntity`：

```java
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ss.mybatis.domain.BaseEntity;

@TableName("orders")
public class OrderEntity extends BaseEntity {
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

`BaseEntity` 只包含 `createDept/createBy/createTime/updateBy/updateTime`，不携带 HTTP 请求参数
或 JSON 注解。插入时只填充空字段；更新时刷新 `updateTime`，存在审计主体时刷新 `updateBy`。

## Mapper

```java
import com.ss.mybatis.mapper.SimpleBaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends SimpleBaseMapper<OrderEntity> {
}
```

`SimpleBaseMapper` 只在官方 `BaseMapper` 上增加 `selectAll()`。批量写入、Join、对象转换和
Service 层仍使用 MyBatis-Plus 或业务应用自己的实现，不由本 starter 强制选择框架。

## 安全分页和排序

```java
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ss.mybatis.page.PageQuery;
import com.ss.mybatis.page.TableData;

PageQuery query = new PageQuery();
query.setPageNum(1);
query.setPageSize(50);
query.setOrderByColumn("createTime,id");
query.setDirection("desc,asc");

Page<OrderEntity> page = orderMapper.selectPage(query.build(500), null);
TableData<OrderEntity> result = TableData.from(page);
```

`createTime` 会转换成 `create_time`。排序列只接受字母开头的 Java/camelCase 或下划线标识符；
点号、空 token、函数、空格、分号和 SQL 注释都会在访问数据库前被拒绝。方向只接受
`asc/desc/ascending/descending`，单个方向可作用于全部列，多方向数量必须与列数量一致。

不要把任意请求参数直接传给数据库原生 SQL。若业务需要按关联表字段排序，应在应用中建立
固定的外部字段到可信 SQL 列表达式映射，而不是放宽 `PageQuery` 校验。

## 审计上下文

基础 starter 不依赖 Auth。应用可提供 `AuditContextProvider`，把自己的认证模型转换为最小审计
上下文：

```java
import com.ss.mybatis.audit.AuditContext;
import com.ss.mybatis.audit.AuditContextProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PersistenceAuditConfiguration {

    @Bean
    AuditContextProvider auditContextProvider(CurrentUser currentUser) {
        return () -> currentUser.isAuthenticated()
                ? new AuditContext(currentUser.userId(), currentUser.departmentId())
                : AuditContext.empty();
    }
}
```

provider 不应查询远程服务或记录用户信息；它会在每次 MyBatis-Plus 自动填充时被调用。未提供
provider 时使用空上下文，时间字段仍会填充，用户和部门字段保持为空或原值。

## 数据库类型识别

```java
import com.ss.mybatis.database.DatabaseMetadata;
import com.ss.mybatis.database.DatabaseType;

DatabaseType type = DatabaseMetadata.detect(dataSource);
```

`DatabaseMetadata` 使用调用方显式传入的 `DataSource`，读取后关闭连接，支持 MySQL/MariaDB、
PostgreSQL、Oracle、SQL Server 和 H2。无法识别时返回 `UNKNOWN`；JDBC 访问失败时抛出
`DatabaseMetadataException`，顶层消息不包含连接信息。

## 覆盖默认 Bean

其他独立模块可通过 `MybatisPlusInterceptorCustomizer` 在分页和乐观锁之前添加需要确定顺序的
`InnerInterceptor`。多个 customizer 按 Spring `Order` 排序执行：

```java
@Bean
@Order(100)
MybatisPlusInterceptorCustomizer applicationCustomizer() {
    return interceptor -> interceptor.addInnerInterceptor(customInnerInterceptor());
}
```

该扩展点只作用于 starter 创建的 `MybatisPlusInterceptor`，不会修改消费者完全自定义的 Bean。

应用已有 MyBatis-Plus 插件配置时，直接声明自己的 `MybatisPlusInterceptor`，starter 会完全回退：

```java
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MybatisConfiguration {

    @Bean
    MybatisPlusInterceptor applicationMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }
}
```

`AuditContextProvider` 和 `MetaObjectHandler` 同样支持消费者覆盖。starter 不修改消费者拦截器，
也不会猜测租户、数据权限或动态数据源的顺序。

## 字段状态缓存

需要把短生命周期设备状态保存在内存，并在状态变化时选择性更新数据库，可继承
`MybatisPlusStatusFieldCacheManager`：

```java
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.ss.mybatis.cache.MybatisPlusStatusFieldCacheManager;

import java.time.Duration;

final class DeviceStatusCache extends
        MybatisPlusStatusFieldCacheManager<DeviceEntity, DeviceService> {

    private final DeviceService service;

    DeviceStatusCache(DeviceService service) {
        super(DeviceEntity.class, Duration.ofSeconds(30));
        this.service = service;
        onUp(deviceId -> eventPublisher.deviceOnline(deviceId));
        onDown(deviceId -> eventPublisher.deviceOffline(deviceId));
    }

    @Override
    protected DeviceService service() {
        return service;
    }

    @Override
    protected SFunction<DeviceEntity, ?> keyField() {
        return DeviceEntity::getId;
    }

    @Override
    protected SFunction<DeviceEntity, ?> valueField() {
        return DeviceEntity::getStatus;
    }
}
```

`record(deviceId, true)` 写入默认上线值 `1` 并在值变化时更新数据库；`cancel(deviceId, true)` 写入
默认下线值 `0`。传 `false` 只更新本地缓存。字段 getter 会通过 MyBatis-Plus 表元数据解析为真实列名，
不拼接调用方输入；表元数据或字段映射不存在时快速失败。缓存默认惰性过期，不会自行启动线程。

更通用的非字符串字段可继承 `MybatisPlusFieldCacheManager`，自定义 `ValueComparator` 和可选的
`updateTimeField()`。外部更新抛出异常时异常向调用方传播，未持久化的新值不会进入缓存。

## 未迁移能力

Honeybee 原模块中的以下内容没有进入基础 starter：

- dynamic-datasource、MP Join 和 P6Spy：依赖和运行时行为独立，使用方应显式选择。
- 与 LoginUser、角色和部门服务绑定的数据权限 SpEL：属于业务授权模型，后续按独立 opt-in 模块评估。
- 静态 Spring Bean 获取、静态数据源缓存和 `findInSet` SQL 字符串拼接。
- Web 全局异常处理和固定 HTTP 响应模型。
- MapStruct 对象转换和反射猜测 VO 泛型。
- 无上限默认分页和把批量操作结果强制返回 `true`。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-springboot-starter/simple-secret-springboot-starter-mybatis-plus \
  clean verify
```

模块测试不连接数据库，覆盖依赖边界、配置、分页注入防护、审计填充、数据库识别、自动配置条件、
消费者 Bean 覆盖和发布资源。

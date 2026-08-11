# Simple Secret Toolbox

`simple-secret-common-toolbox` 提供可在普通 Java 17 项目中使用的基础工具，不依赖 Spring、Hutool、
Lombok 或其他第三方运行时库。当前包含安全 URI 格式化、getter 属性解析和线程安全过期缓存。

## Maven 依赖

在项目的 `pom.xml` 中先导入 Simple Secret BOM，再声明无版本号的 toolbox：

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
        <artifactId>simple-secret-common-toolbox</artifactId>
    </dependency>
</dependencies>
```

## 过期缓存

```java
import com.ss.common.toolbox.cache.ExpiringCache;

import java.time.Duration;

try (ExpiringCache<String, DeviceState> cache =
             new ExpiringCache<>(Duration.ofSeconds(30))) {
    cache.put("device-1", new DeviceState("online"));

    DeviceState state = cache.computeIfAbsent(
            "device-2", deviceRepository::loadState);
}
```

默认只在 `get`、`containsKey`、`size` 等访问时惰性删除过期项，不会在构造时创建线程。需要主动清理
长期不访问的 key 时显式开启守护清理线程，并在组件销毁时关闭：

```java
ExpiringCache<String, DeviceState> cache =
        new ExpiringCache<>(Duration.ofSeconds(30));
cache.scheduleCleanup(Duration.ofSeconds(5));

cache.addRemovalListener((key, value, cause) ->
        auditService.recordRemoval(key, cause));

// Spring Bean 可通过 @Bean(destroyMethod = "close") 管理生命周期。
cache.close();
```

`computeIfAbsent` 对同一个 key 串行加载，加载器返回 `null` 时不缓存。缓存不接受空 key 或空 value。
监听器会收到 `EXPIRED`、`EXPLICIT` 或 `REPLACED` 原因；监听器异常会被隔离，不会让缓存主操作失败。

## 外部存储联动

业务需要在状态变化时选择性同步数据库或远程存储，可继承 `DatabaseExpiringCacheManager`：

```java
import com.ss.common.toolbox.cache.DatabaseExpiringCacheManager;
import com.ss.common.toolbox.cache.ValueComparator;

final class DeviceStateCache
        extends DatabaseExpiringCacheManager<String, String> {

    DeviceStateCache() {
        super(Duration.ofSeconds(30), ValueComparator.natural());
    }

    @Override
    protected boolean updateStore(String deviceId, String state) {
        return deviceRepository.updateState(deviceId, state);
    }
}
```

调用 `put(key, value, true)` 时，仅在 key 首次出现或值变化时更新外部存储；传 `false` 只更新本地缓存。
外部更新返回 `false` 会触发失败回调但仍写入缓存；外部更新抛出异常时异常向调用方传播，并且不会发布
未持久化的新缓存值。

## 动态列契约

动态列包提供纯 Java 的数据、服务和转换器契约，不提供数据库建表，也不提供 Spring 自动配置。调用方负责定义
具体列属性、数据标识和持久化实现：

```java
import com.ss.common.toolbox.dynamiccolumn.ColumnData;
import com.ss.common.toolbox.dynamiccolumn.ColumnProperties;
import com.ss.common.toolbox.dynamiccolumn.IDynamicColumnsService;
import com.ss.common.toolbox.dynamiccolumn.converter.ColumnDataConverter;

final class DynamicColumnExample {

    static void configureAndConvert() {
        TicketColumnProperties column = new TicketColumnProperties();
        column.setColumnId("priority");
        column.setBusinessType("ticket");
        column.setName("Priority");
        column.setType("integer");
        column.setOrder(1);

        TicketColumnData data = new TicketColumnData("priority", "ticket-42");

        ColumnDataConverter<Integer, String> converter = new ColumnDataConverter<>() {
            @Override
            public Integer ori2db(String source) {
                return Integer.valueOf(source);
            }

            @Override
            public String db2ori(Integer target) {
                return target.toString();
            }
        };

        boolean created = new TicketColumnService().createColumn(column);
        Integer databaseValue = converter.ori2db("42");
        String originalValue = converter.db2ori(databaseValue);
    }

    private static final class TicketColumnProperties extends ColumnProperties {
    }

    private record TicketColumnData(String columnId, String businessId) implements ColumnData {
    }

    private static final class TicketColumnService
            implements IDynamicColumnsService<TicketColumnProperties, TicketColumnData> {

        @Override
        public boolean createColumn(TicketColumnProperties column) {
            // 在此调用应用自己的存储实现。
            return true;
        }
    }
}
```

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-common/simple-secret-common-toolbox clean verify
```

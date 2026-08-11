# Simple Secret Dict

`simple-secret-common-dict` 为普通 Java 17 项目提供显式字典注册、枚举查询、TTL 缓存、code/label
转换和对象字段翻译。模块只依赖 `simple-secret-common-toolbox` 的 JDK 缓存，不依赖 Spring、Hutool、
Lombok、数据库或 Redis。

## Maven 依赖

推荐先导入 Simple Secret BOM，再声明 dict 模块：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-common-dict</artifactId>
</dependency>
```

未使用 BOM 时需要显式指定 `<version>1.1.0</version>`。toolbox 会作为必要的传递依赖获得，应用
不需要再次声明。

## 枚举字典

业务枚举实现 `DictValue`，最少提供 code 和 label：

```java
import com.ss.dict.model.DictValue;

enum Sex implements DictValue {
    MALE("1", "男"),
    FEMALE("0", "女");

    private final String code;
    private final String label;

    Sex(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getDictCode() {
        return code;
    }

    @Override
    public String getDictLabel() {
        return label;
    }
}
```

可直接查询枚举，也可以注册到实例级 `DictionaryRegistry`：

```java
import com.ss.dict.DictEnums;
import com.ss.dict.DictionaryRegistry;

Sex sex = DictEnums.find(Sex.class, "1");

try (DictionaryRegistry registry = new DictionaryRegistry()) {
    registry.registerEnum("sex", Sex.class);
    String label = registry.find("sex", "0").label();
}
```

`DictValue` 默认 type 为 `default`、scope 为 `GLOBAL`、scopeCode 为 `global`。需要区分类型或租户
范围时覆盖相应方法。

## 业务数据源与缓存

注册表不扫描容器，也不会按类名字符串加载代码。业务必须显式注册实际使用的数据源：

```java
import com.ss.dict.DictionaryRegistry;

import java.time.Duration;

DictionaryRegistry registry = new DictionaryRegistry(Duration.ofSeconds(30));
registry.register("system.status", statusRepository::listDictValues);

// 实时读取，每次调用数据源。
var current = registry.query("system.status");

// 同一个 key 并发缺失时只调用一次数据源。
var cached = registry.queryCached("system.status");

// 本次写入使用独立 TTL。
var shortLived = registry.queryCached("system.status", Duration.ofSeconds(5));

// 数据变更后显式失效；不会删除数据源注册。
registry.invalidate("system.status");
registry.clearCache();
registry.close();
```

缓存只惰性过期，不创建后台线程。数据源返回 `null`、包含非法元素或抛出异常时，异常向调用方传播，
失败结果不会进入缓存。查询返回不可变的 `DictElement` 快照，业务对象后续修改不会污染缓存。

重复注册同一个 key 会抛出 `IllegalStateException`，防止不同组件静默覆盖字典来源。查询未注册 key
会抛出 `IllegalArgumentException`；`registerEnum` 传入普通类时也会在注册阶段立即失败。

## code 与 label 转换

数据库字典服务可以实现 `DictService`，只提供一种类型的全部值，其余转换由接口完成：

```java
import com.ss.dict.service.DictService;

DictService service = dictType -> dictionaryRepository.findByType(dictType);

String labels = service.getDictLabel("status", "1,0");
String codes = service.getDictCode("status", "启用,禁用");
```

默认分隔符为逗号，也可在第三个参数传入字面量分隔符。多个值保持输入顺序，未知 code 或 label
保留原值，不会被静默删除。

## 对象字段翻译

在编码字段上使用 `@DictField`。默认展示字段名为“源字段名 + `DisplayLabel`”：

```java
import com.ss.dict.DictionaryParser;
import com.ss.dict.annotation.DictField;

final class UserView {
    @DictField("sex")
    private String sex;
    private String sexDisplayLabel;
}

DictionaryRegistry registry = new DictionaryRegistry();
registry.registerEnum("sex", Sex.class);

DictionaryParser parser = new DictionaryParser(registry);
parser.parse(userView);
parser.parseAll(userViews);
```

固定 type、动态 type 和多值字段示例：

```java
final class BiologyView {
    private String category;

    @DictField(value = "sex", typeField = "category")
    private String sex;
    private String sexDisplayLabel;

    @DictField(value = "tags", multiple = true, separator = "|")
    private String tagCodes;
    private String tagCodesDisplayLabel;
}
```

固定类型使用 `type = "PERSON"`；自定义目标字段使用 `labelField = "sexName"`。`type` 和
`typeField` 不能同时配置。单值未命中时写入原 code，多值按输入顺序翻译并保留未知 code；源字段为
`null` 时不修改现有展示字段。

注解配置冲突、目标字段不存在、目标字段不能接收 String 或字段无法访问时抛出
`DictionaryMappingException`，不会像旧实现一样静默跳过错误。字段元数据通过 `ClassValue` 缓存，
支持继承字段且不会持有全局 Spring 容器。源字段、动态类型字段和展示字段都必须是实例字段，静态字段
会在元数据检查阶段被拒绝，避免修改全局状态。

## 从旧字典模块迁移

- 将类名字符串改为 `registry.registerEnum("key", EnumType.class)`。
- 将 Spring Bean 名称或约定 `list()` 方法改为显式 `registry.register("key", source)`。
- 将旧字段注解替换为 `@DictField`，展示字段默认后缀仍为 `DisplayLabel`。
- 将静态查询改为持有 `DictionaryRegistry`/`DictionaryParser` 实例，生命周期由应用管理。
- 数据源和反射错误会直接暴露；调用方应修复配置或在业务边界记录并转换异常。

## 验证

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn \
  -pl simple-secret-common/simple-secret-common-dict -am clean verify
```

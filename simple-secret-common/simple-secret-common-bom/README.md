# Simple Secret Common BOM

`simple-secret-common-bom` 统一管理 Simple Secret 保留模块和关键第三方依赖版本。它是 Maven BOM，不包含
Java 类，也不会给应用传递任何运行时依赖。

## 导入方式

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
```

导入后，业务依赖不需要重复声明版本：

```xml
<dependency>
    <groupId>com.ss</groupId>
    <artifactId>simple-secret-springboot-starter-mqttv5</artifactId>
</dependency>
```

## 版本管理范围

- 所有当前保留的 common、plugin 和 starter 模块。
- Jackson、Netty、Apache POI、Commons、Bouncy Castle 和 JNA。
- BOM 不管理已经删除的 starter，也不恢复 `simple-secret-common-json`。

## 解析顺序

如果应用同时导入 Simple Secret BOM 与 Spring Boot BOM，应将 Simple Secret BOM 放在前面，使本项目针对
安全补丁和依赖收敛选定的版本优先生效。升级 BOM 前应检查传递依赖、二进制兼容和第三方安全公告。

## 发布流程

1. 根项目更新 `revision` 和 BOM 中对应模块版本。
2. 运行全量 `mvn verify`，确认依赖收敛和禁止依赖规则通过。
3. 发布 BOM 与各模块构件。
4. 使用 `integration-tests` 从本地或制品仓库解析已发布 POM，验证第三方消费方式。

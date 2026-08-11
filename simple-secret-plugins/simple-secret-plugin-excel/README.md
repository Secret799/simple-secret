# Simple Secret Excel Plugin

`simple-secret-plugin-excel` 是基于 EasyExcel 4.0.3 的纯 Java Excel 插件，提供多 Sheet 导出、有界批量导入、业务校验、错误工作簿、合并单元格回填、下拉框、多段合并、安全列宽和树形导出。

模块不依赖 Spring、Servlet、JSON starter、Honeybee、Hutool、Guava、Lombok 或 Jakarta Validation。公共 API 只接收 `InputStream`、`OutputStream` 和 JDK 集合，适用于 Web、任务调度、消息消费和命令行程序。

## Maven 依赖

推荐先导入 BOM：

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
        <artifactId>simple-secret-plugin-excel</artifactId>
    </dependency>
</dependencies>
```

生产依赖只有 `com.alibaba:easyexcel` 及其不可避免的 POI、XMLBeans 等传递依赖。

## 模型导出

```java
import com.alibaba.excel.annotation.ExcelProperty;
import com.ss.excel.exporter.ExcelExporter;
import com.ss.excel.model.ExcelSheet;

import java.io.ByteArrayOutputStream;
import java.util.List;

class UserRow {
    @ExcelProperty("ID")
    private Integer id;

    @ExcelProperty("Name")
    private String name;

    // EasyExcel 导入时需要无参构造器和 setter；此处省略。
}

ExcelSheet<UserRow> users = ExcelSheet.<UserRow>builder()
        .name("users")
        .modelType(UserRow.class)
        .rows(List.of(user))
        .build();

ByteArrayOutputStream output = new ByteArrayOutputStream();
new ExcelExporter().write(output, List.of(users));
```

`ExcelSheet` 必须且只能配置 `modelType` 或 `head` 其中一个。名称会移除 Excel 禁止字符并限制为 31 个字符；行、表头和写入处理器均做不可变防御性复制。

## 自定义表头和多 Sheet

```java
ExcelSheet<List<Object>> summary = ExcelSheet.<List<Object>>builder()
        .name("summary")
        .head(List.of(
                List.of("Device", "ID"),
                List.of("Device", "Status")))
        .rows(List.of(
                List.of("dev-1", "online"),
                List.of("dev-2", "offline")))
        .build();

new ExcelExporter().write(output, List.of(users, summary));
```

单个工作簿最多写入 100 个 Sheet。写入器使用明确的 Sheet 索引，并默认设置水平、垂直居中样式。

## 流所有权和 Web 下载

插件不会关闭调用方传入的输入流或输出流。Web 响应头由宿主应用负责设置，因此插件不需要 Servlet 依赖。Spring MVC 宿主可以这样使用：

```java
String fileName = java.net.URLEncoder.encode(
        "users.xlsx", java.nio.charset.StandardCharsets.UTF_8)
        .replace("+", "%20");
response.setContentType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
response.setHeader("Content-Disposition",
        "attachment; filename*=UTF-8''" + fileName);

new ExcelExporter().write(response.getOutputStream(), List.of(users));
response.flushBuffer();
```

不要在插件调用完成前关闭、复用或并发写入同一个流。

## 有界批量导入

```java
import com.ss.excel.importer.ExcelImportRequest;
import com.ss.excel.importer.ExcelImporter;
import com.ss.excel.model.ExcelImportResult;

ExcelImportRequest<UserRow> request = ExcelImportRequest.<UserRow>builder()
        .modelType(UserRow.class)
        .sheetNo(0)
        .headRowCount(1)
        .batchSize(500)
        .maxRows(100_000)
        .attribute("tenantId", "north")
        .processor((rows, context) -> {
            userRepository.saveAll(rows);
            return Map.of();
        })
        .build();

ExcelImportResult<UserRow> result =
        new ExcelImporter().read(inputStream, request);
```

普通导入在内存中最多保留一个批次和失败行。默认批次大小为 500，默认最大数据行为 100000。`ExcelBatchContext` 提供零基批次索引、当前批次第一行的 Excel 一基行号和不可变 attributes。

可以用 `.sheetName("users")` 替代 `.sheetNo(0)`。二者最后一次配置生效。

## 业务校验

处理器返回两层错误映射：外层键是当前批次内的零基行索引，内层键是零基列索引。

```java
.processor((rows, context) -> {
    Map<Integer, Map<Integer, String>> errors = new LinkedHashMap<>();
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
        UserRow row = rows.get(rowIndex);
        if (row.getName() == null || row.getName().isBlank()) {
            errors.put(rowIndex, Map.of(1, "Name is required"));
        }
    }
    return errors;
})
```

插件会校验所有行列索引，错误文本最多保留 1024 个字符。成功数是未出现在错误映射中的行数；内存中只保留失败行，业务处理器负责持久化成功行。

## 错误工作簿

```java
if (!result.getErrors().isEmpty()) {
    ExcelSheet<UserRow> errorSheet = ExcelSheet.<UserRow>builder()
            .name("import-errors")
            .modelType(UserRow.class)
            .build();

    new ExcelExporter().writeErrors(
            errorOutputStream, errorSheet, result.getErrors());
}
```

错误工作簿只写失败行，并在对应列创建红色单元格和批注。即使原行没有该列的单元格，也会安全创建空单元格。批注文本同样限制为 1024 个字符。

## 合并单元格导入

```java
ExcelImportRequest<UserRow> request = ExcelImportRequest.<UserRow>builder()
        .modelType(UserRow.class)
        .fillMergedCells(true)
        .maxRows(20_000)
        .processor(processor)
        .build();
```

启用后，插件读取 EasyExcel 的合并区域元数据，将区域左上角值回填到对应模型字段，再按 `batchSize` 调用处理器。字段应使用 `@ExcelProperty(index = ...)` 明确列号；隐式列号按声明顺序补齐。

合并回填必须等完整 Sheet 的合并元数据读取完成，因此会在内存中保留该 Sheet 的数据，仍受 `maxRows` 硬限制。大文件应保持 `fillMergedCells(false)`。重叠范围、倒置范围、跨表头范围、未读取行和未映射列都会失败，不会静默跳过。

## 下拉框、合并和列宽

```java
import com.ss.excel.strategy.DropdownWriteHandler;
import com.ss.excel.strategy.ManyRowMergeStrategy;
import com.ss.excel.strategy.SafeColumnWidthStyleStrategy;

DropdownWriteHandler dropdown = new DropdownWriteHandler(
        1, 100, Map.of(2, List.of("enabled", "disabled")));

ManyRowMergeStrategy merge = new ManyRowMergeStrategy(
        0, List.of(1, 3, 5, 8));

ExcelSheet<List<Object>> sheet = ExcelSheet.<List<Object>>builder()
        .name("devices")
        .head(head)
        .rows(rows)
        .addWriteHandler(dropdown)
        .addWriteHandler(merge)
        .addWriteHandler(new SafeColumnWidthStyleStrategy(40))
        .build();
```

- 下拉范围和列号均为零基，行范围包含首尾；显式选项总长度不能超过 Excel 的 255 字符限制。
- 普通合并参数是物理行号的 `[开始, 结束, 开始, 结束]` 对，必须排序、完整且互不重叠。
- 连续模式 `new ManyRowMergeStrategy(true, 0, List.of(1, 3, 8))` 生成 `1-3` 和 `4-8`。
- 安全列宽按文本增长，并限制为 1 到 255 个字符宽度，避免 POI 超限异常。

## 树形导出

```java
import com.ss.excel.tree.ExcelTreeExport;
import com.ss.excel.tree.ExcelTreeExporter;
import com.ss.excel.tree.ExcelTreeNode;

ExcelTreeNode<Device> root = ExcelTreeNode.of(region, List.of(
        ExcelTreeNode.leaf(deviceA),
        ExcelTreeNode.leaf(deviceB)));

ExcelTreeExport tree = new ExcelTreeExporter().assemble(
        List.of(root),
        List.of(Device::getName, Device::getCode),
        1);

ExcelSheet.Builder<List<Object>> builder = ExcelSheet.<List<Object>>builder()
        .name("tree")
        .head(treeHead)
        .rows(tree.getRows());
tree.getWriteHandlers().forEach(builder::addWriteHandler);

new ExcelExporter().write(output, List.of(builder.build()));
```

每个叶节点生成一行，每一层占用与 extractor 数量相同的列。父节点跨越多个叶行时会为其列生成合并处理器。支持多根和不等深树，结果行和处理器列表不可变。

## 限制和异常

- Java 版本：17。
- 最大 Sheet 数：100。
- 默认批次大小：500。
- 默认最大导入数据行：100000。
- 最大错误批注长度：1024。
- 输入输出流始终由调用方拥有。
- 参数错误抛出 `IllegalArgumentException` 或 `NullPointerException`。
- EasyExcel/POI 运行失败统一包装为 `ExcelOperationException`。
- `ExcelOperationException` 只包含操作名和 Sheet 标识，不会把单元格正文、业务错误或底层异常消息写入公共 message；底层 cause 仍供可信日志系统诊断。

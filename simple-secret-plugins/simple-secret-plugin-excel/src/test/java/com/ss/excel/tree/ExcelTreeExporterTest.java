package com.ss.excel.tree;

import com.ss.excel.exporter.ExcelExporter;
import com.ss.excel.model.ExcelSheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelTreeExporterTest {

    private final ExcelTreeExporter exporter = new ExcelTreeExporter();

    @Test
    void returnsEmptyImmutableResultForEmptyTree() {
        ExcelTreeExport result = exporter.<NodeValue>assemble(List.of(), List.of(NodeValue::name), 1);

        assertThat(result.getRows()).isEmpty();
        assertThat(result.getRows()).isUnmodifiable();
        assertThat(result.getWriteHandlers()).isEmpty();
        assertThat(result.getWriteHandlers()).isUnmodifiable();
    }

    @Test
    void assemblesMultipleRootsAndUnevenDepthWithParentMerges() throws IOException {
        ExcelTreeNode<NodeValue> root = ExcelTreeNode.of(new NodeValue("root", "R"), List.of(
                ExcelTreeNode.leaf(new NodeValue("first", "A")),
                ExcelTreeNode.of(new NodeValue("branch", "B"), List.of(
                        ExcelTreeNode.leaf(new NodeValue("deep-1", "C")),
                        ExcelTreeNode.leaf(new NodeValue("deep-2", "D"))))));
        ExcelTreeNode<NodeValue> secondRoot = ExcelTreeNode.leaf(new NodeValue("second", "S"));
        List<Function<? super NodeValue, ?>> extractors = List.of(NodeValue::name, NodeValue::code);

        ExcelTreeExport result = exporter.assemble(List.of(root, secondRoot), extractors, 1);

        assertThat(result.getRows()).containsExactly(
                List.of("root", "R", "first", "A"),
                java.util.Arrays.asList(null, null, "branch", "B", "deep-1", "C"),
                java.util.Arrays.asList(null, null, null, null, "deep-2", "D"),
                List.of("second", "S"));
        assertThat(result.getRows()).isUnmodifiable();
        assertThat(result.getRows().get(0)).isUnmodifiable();

        ExcelSheet.Builder<List<Object>> sheet = ExcelSheet.<List<Object>>builder()
                .name("tree")
                .head(List.of(List.of("L1 Name"), List.of("L1 Code"),
                        List.of("L2 Name"), List.of("L2 Code"),
                        List.of("L3 Name"), List.of("L3 Code")))
                .rows(result.getRows());
        result.getWriteHandlers().forEach(sheet::addWriteHandler);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new ExcelExporter().write(output, List.of(sheet.build()));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getSheetAt(0).getMergedRegions()).containsExactlyInAnyOrder(
                    new CellRangeAddress(1, 3, 0, 0),
                    new CellRangeAddress(1, 3, 1, 1),
                    new CellRangeAddress(2, 3, 2, 2),
                    new CellRangeAddress(2, 3, 3, 3));
        }
    }

    private record NodeValue(String name, String code) {
    }
}

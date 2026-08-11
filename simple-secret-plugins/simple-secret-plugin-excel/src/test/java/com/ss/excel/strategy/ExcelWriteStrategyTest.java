package com.ss.excel.strategy;

import com.ss.excel.exporter.ExcelExporter;
import com.ss.excel.model.ExcelSheet;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelWriteStrategyTest {

    private final ExcelExporter exporter = new ExcelExporter();

    @Test
    void placesDropdownOptionsOnInclusiveBounds() throws IOException {
        DropdownWriteHandler dropdown = new DropdownWriteHandler(2, 10,
                Map.of(1, List.of("enabled", "disabled")));

        try (Workbook workbook = writeWorkbook(dropdown)) {
            List<? extends DataValidation> validations = workbook.getSheetAt(0).getDataValidations();
            assertThat(validations).hasSize(1);
            assertThat(validations.get(0).getRegions().getCellRangeAddresses())
                    .containsExactly(new CellRangeAddress(2, 10, 1, 1));
            assertThat(validations.get(0).getValidationConstraint().getExplicitListValues())
                    .containsExactly("enabled", "disabled");
        }
    }

    @Test
    void rejectsInvalidDropdownAndMergeConfiguration() {
        assertThatThrownBy(() -> new DropdownWriteHandler(-1, 2, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DropdownWriteHandler(3, 2, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DropdownWriteHandler(1, 2, Map.of(-1, List.of("x"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DropdownWriteHandler(1, 2, Map.of(0, List.of())))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ManyRowMergeStrategy(false, 0, List.of(3, 2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManyRowMergeStrategy(false, 0, List.of(1, 3, 3, 5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
        assertThatThrownBy(() -> new ManyRowMergeStrategy(true, 0, List.of(1, 3, 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesPairAndContinuousMergeRangesOnce() throws IOException {
        ManyRowMergeStrategy pairs = new ManyRowMergeStrategy(false, 0, List.of(1, 2, 4, 5));
        ManyRowMergeStrategy continuous = new ManyRowMergeStrategy(true, 1, List.of(1, 2, 5));

        try (Workbook workbook = writeWorkbook(pairs, continuous)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getMergedRegions()).containsExactlyInAnyOrder(
                    new CellRangeAddress(1, 2, 0, 0),
                    new CellRangeAddress(4, 5, 0, 0),
                    new CellRangeAddress(1, 2, 1, 1),
                    new CellRangeAddress(3, 5, 1, 1));
        }
    }

    @Test
    void capsColumnWidthAndHandlesNonStringCells() throws IOException {
        SafeColumnWidthStyleStrategy width = new SafeColumnWidthStyleStrategy(20);
        ExcelSheet<List<Object>> sheet = ExcelSheet.<List<Object>>builder()
                .name("width")
                .head(List.of(List.of("Text"), List.of("Number")))
                .rows(List.of(List.of("x".repeat(500), 12345)))
                .addWriteHandler(width)
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        exporter.write(output, List.of(sheet));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getSheetAt(0).getColumnWidth(0)).isEqualTo(20 * 256);
            assertThat(workbook.getSheetAt(0).getColumnWidth(1)).isBetween(1, 20 * 256);
        }
    }

    @Test
    void capsWidthsBelowTheWorkbookDefault() throws IOException {
        SafeColumnWidthStyleStrategy width = new SafeColumnWidthStyleStrategy(4);
        ExcelSheet<List<Object>> sheet = ExcelSheet.<List<Object>>builder()
                .name("narrow")
                .head(List.of(List.of("Text")))
                .rows(List.of(List.of("x".repeat(100))))
                .addWriteHandler(width)
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        exporter.write(output, List.of(sheet));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getSheetAt(0).getColumnWidth(0)).isEqualTo(4 * 256);
        }
    }

    private Workbook writeWorkbook(com.alibaba.excel.write.handler.WriteHandler... handlers) throws IOException {
        ExcelSheet.Builder<List<Object>> builder = ExcelSheet.<List<Object>>builder()
                .name("strategies")
                .head(List.of(List.of("A"), List.of("B")))
                .rows(List.of(
                        List.of("a", "1"), List.of("b", "2"), List.of("c", "3"),
                        List.of("d", "4"), List.of("e", "5")));
        for (com.alibaba.excel.write.handler.WriteHandler handler : handlers) {
            builder.addWriteHandler(handler);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        exporter.write(output, List.of(builder.build()));
        return new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()));
    }
}

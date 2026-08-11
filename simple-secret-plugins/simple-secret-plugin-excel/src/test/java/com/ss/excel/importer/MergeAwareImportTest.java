package com.ss.excel.importer;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.metadata.CellExtra;
import com.ss.excel.exception.ExcelOperationException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MergeAwareImportTest {

    private final ExcelImporter importer = new ExcelImporter();

    @Test
    void fillsVerticalAndHorizontalMergedCellsBeforeBatchProcessing() throws IOException {
        List<List<String>> values = new ArrayList<>();
        ExcelImportRequest<MergedRow> request = ExcelImportRequest.<MergedRow>builder()
                .modelType(MergedRow.class)
                .batchSize(2)
                .fillMergedCells(true)
                .processor((rows, context) -> {
                    values.addAll(rows.stream()
                            .map(row -> List.of(row.getGroup(), row.getDetail()))
                            .toList());
                    return Map.of();
                })
                .build();

        importer.read(new ByteArrayInputStream(mergedWorkbook()), request);

        assertThat(values).containsExactly(
                List.of("G1", "one"),
                List.of("G1", "two"),
                List.of("H1", "H1"),
                List.of("solo", "unchanged"));
    }

    @Test
    void respectsHeadRowOffsetAndMaximumRows() throws IOException {
        List<String> groups = new ArrayList<>();
        ExcelImportRequest<MergedRow> request = ExcelImportRequest.<MergedRow>builder()
                .modelType(MergedRow.class)
                .headRowCount(2)
                .fillMergedCells(true)
                .processor((rows, context) -> {
                    groups.addAll(rows.stream().map(MergedRow::getGroup).toList());
                    return Map.of();
                })
                .build();
        importer.read(new ByteArrayInputStream(workbookWithTwoHeadRows()), request);
        assertThat(groups).containsExactly("G1", "G1");

        ExcelImportRequest<MergedRow> limited = ExcelImportRequest.<MergedRow>builder()
                .modelType(MergedRow.class)
                .fillMergedCells(true)
                .maxRows(1)
                .processor((rows, context) -> Map.of())
                .build();
        assertThatThrownBy(() -> importer.read(new ByteArrayInputStream(mergedWorkbook()), limited))
                .isInstanceOf(ExcelOperationException.class)
                .hasRootCauseMessage("Excel row limit exceeded: 1");
    }

    @Test
    void rejectsOverlappingOrHeaderCrossingMergeMetadata() {
        CellExtra first = merge(1, 0, 2, 0);
        CellExtra overlapping = merge(2, 0, 3, 0);
        assertThatThrownBy(() -> ExcelImporter.validateMergeRanges(List.of(first, overlapping), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");

        CellExtra crossingHeader = merge(0, 0, 1, 0);
        assertThatThrownBy(() -> ExcelImporter.validateMergeRanges(List.of(crossingHeader), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header");
    }

    private byte[] mergedWorkbook() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("merged");
            createRow(sheet, 0, "Group", "Detail");
            createRow(sheet, 1, "G1", "one");
            createRow(sheet, 2, null, "two");
            createRow(sheet, 3, "H1", null);
            createRow(sheet, 4, "solo", "unchanged");
            sheet.addMergedRegion(new CellRangeAddress(1, 2, 0, 0));
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 1));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithTwoHeadRows() throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("merged");
            createRow(sheet, 0, "Top", "Top");
            createRow(sheet, 1, "Group", "Detail");
            createRow(sheet, 2, "G1", "one");
            createRow(sheet, 3, null, "two");
            sheet.addMergedRegion(new CellRangeAddress(2, 3, 0, 0));
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void createRow(Sheet sheet, int rowIndex, String first, String second) {
        Row row = sheet.createRow(rowIndex);
        if (first != null) {
            row.createCell(0).setCellValue(first);
        }
        if (second != null) {
            row.createCell(1).setCellValue(second);
        }
    }

    private CellExtra merge(int firstRow, int firstColumn, int lastRow, int lastColumn) {
        return new CellExtra(CellExtraTypeEnum.MERGE, null,
                firstRow, lastRow, firstColumn, lastColumn);
    }

    public static final class MergedRow {

        @ExcelProperty(index = 0)
        private String group;

        @ExcelProperty(index = 1)
        private String detail;

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }
}

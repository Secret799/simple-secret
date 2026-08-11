package com.ss.excel.importer;

import com.alibaba.excel.annotation.ExcelProperty;
import com.ss.excel.exception.ExcelOperationException;
import com.ss.excel.model.ExcelImportResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelImporterTest {

    private final ExcelImporter importer = new ExcelImporter();

    @Test
    void readsExactBatchesHeadersAttributesAndErrors() throws IOException {
        List<List<Integer>> batches = new ArrayList<>();
        List<Integer> firstRows = new ArrayList<>();
        ExcelImportRequest<ImportRow> request = ExcelImportRequest.<ImportRow>builder()
                .modelType(ImportRow.class)
                .batchSize(2)
                .attribute("tenant", "north")
                .processor((rows, context) -> {
                    batches.add(rows.stream().map(ImportRow::getId).toList());
                    firstRows.add(context.getFirstRowNumber());
                    assertThat(context.getAttributes()).containsEntry("tenant", "north").isUnmodifiable();
                    if (context.getBatchIndex() == 1) {
                        return Map.of(0, Map.of(1, "invalid name"));
                    }
                    return Map.of();
                })
                .build();

        ExcelImportResult<ImportRow> result = importer.read(
                new ByteArrayInputStream(workbook(1, 2, 3, 4, 5)), request);

        assertThat(batches).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
        assertThat(firstRows).containsExactly(2, 4, 6);
        assertThat(result.getHeaders()).containsExactly(
                Map.entry(0, "ID"), Map.entry(1, "Name"));
        assertThat(result.getSuccessCount()).isEqualTo(4);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getTotalCount()).isEqualTo(5);
        assertThat(result.getErrors().get(0).getRowNumber()).isEqualTo(4);
        assertThat(result.getErrors().get(0).getValue().getId()).isEqualTo(3);
    }

    @Test
    void rejectsInvalidProcessorIndexesAndTruncatesMessages() throws IOException {
        ExcelImportRequest<ImportRow> invalidRow = request((rows, context) ->
                Map.of(rows.size(), Map.of(0, "outside batch")));
        assertThatThrownBy(() -> importer.read(new ByteArrayInputStream(workbook(1)), invalidRow))
                .isInstanceOf(ExcelOperationException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);

        ExcelImportRequest<ImportRow> invalidColumn = request((rows, context) ->
                Map.of(0, Map.of(-1, "invalid column")));
        assertThatThrownBy(() -> importer.read(new ByteArrayInputStream(workbook(1)), invalidColumn))
                .isInstanceOf(ExcelOperationException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);

        ExcelImportRequest<ImportRow> longMessage = request((rows, context) ->
                Map.of(0, Map.of(0, "x".repeat(2048))));
        ExcelImportResult<ImportRow> result = importer.read(
                new ByteArrayInputStream(workbook(1)), longMessage);
        assertThat(result.getErrors().get(0).getColumnErrors().get(0)).hasSize(1024);
    }

    @Test
    void enforcesMaximumRowsAndWrapsConversionFailuresWithoutSecrets() throws IOException {
        ExcelImportRequest<ImportRow> limited = ExcelImportRequest.<ImportRow>builder()
                .modelType(ImportRow.class)
                .maxRows(1)
                .processor((rows, context) -> Map.of())
                .build();
        assertThatThrownBy(() -> importer.read(new ByteArrayInputStream(workbook(1, 2)), limited))
                .isInstanceOf(ExcelOperationException.class)
                .hasMessageContaining("read")
                .hasRootCauseMessage("Excel row limit exceeded: 1");

        byte[] invalidWorkbook = workbookWithTextId("password=secret");
        assertThatThrownBy(() -> importer.read(
                new ByteArrayInputStream(invalidWorkbook), request((rows, context) -> Map.of())))
                .isInstanceOf(ExcelOperationException.class)
                .hasMessageNotContaining("secret")
                .hasMessageNotContaining("password");
    }

    @Test
    void keepsCallerInputStreamOpen() throws IOException {
        TrackingInputStream input = new TrackingInputStream(workbook(1));

        importer.read(input, request((rows, context) -> Map.of()));

        assertThat(input.closed).isFalse();
        assertThat(input.read()).isEqualTo(-1);
    }

    private ExcelImportRequest<ImportRow> request(ExcelBatchProcessor<ImportRow> processor) {
        return ExcelImportRequest.<ImportRow>builder()
                .modelType(ImportRow.class)
                .processor(processor)
                .build();
    }

    private byte[] workbook(int... ids) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("users");
            Row head = sheet.createRow(0);
            head.createCell(0).setCellValue("ID");
            head.createCell(1).setCellValue("Name");
            for (int index = 0; index < ids.length; index++) {
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(ids[index]);
                row.createCell(1).setCellValue("user-" + ids[index]);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithTextId(String id) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("users");
            Row head = sheet.createRow(0);
            head.createCell(0).setCellValue("ID");
            head.createCell(1).setCellValue("Name");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(id);
            row.createCell(1).setCellValue("invalid");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public static final class ImportRow {

        @ExcelProperty(index = 0)
        private Integer id;

        @ExcelProperty(index = 1)
        private String name;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackingInputStream(byte[] buffer) {
            super(buffer);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}

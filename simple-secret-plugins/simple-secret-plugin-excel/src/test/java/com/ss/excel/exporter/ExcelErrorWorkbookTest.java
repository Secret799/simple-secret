package com.ss.excel.exporter;

import com.ss.excel.exception.ExcelOperationException;
import com.ss.excel.model.ExcelRowError;
import com.ss.excel.model.ExcelSheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelErrorWorkbookTest {

    private final ExcelExporter exporter = new ExcelExporter();

    @Test
    void writesCommentsToErrorColumnsAndCreatesMissingCells() throws IOException {
        ExcelSheet<List<Object>> sheet = ExcelSheet.<List<Object>>builder()
                .name("errors")
                .head(List.of(List.of("ID"), List.of("Name"), List.of("Remark")))
                .rows(List.of())
                .build();
        String longMessage = "x".repeat(2048);
        List<ExcelRowError<List<Object>>> errors = List.of(
                new ExcelRowError<>(7, List.of(1, "Alice"), Map.of(2, longMessage)));
        TrackingOutputStream output = new TrackingOutputStream();

        exporter.writeErrors(output, sheet, errors);

        assertThat(output.closed).isFalse();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            Cell errorCell = workbook.getSheetAt(0).getRow(1).getCell(2);
            assertThat(errorCell).isNotNull();
            assertThat(errorCell.getCellComment()).isNotNull();
            assertThat(errorCell.getCellComment().getString().getString()).hasSize(1024);
            assertThat(errorCell.getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.RED.getIndex());
        }
    }

    @Test
    void rejectsMissingErrorsAndKeepsCauseSecretsOutOfMessage() {
        ExcelSheet<List<Object>> sheet = ExcelSheet.<List<Object>>builder()
                .name("errors")
                .head(List.of(List.of("ID")))
                .build();
        assertThatThrownBy(() -> exporter.writeErrors(new ByteArrayOutputStream(), sheet, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        OutputStream failingOutput = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw new IOException("password=secret");
            }
        };
        ExcelRowError<List<Object>> error = new ExcelRowError<>(2, List.of(1), Map.of(0, "bad"));
        assertThatThrownBy(() -> exporter.writeErrors(failingOutput, sheet, List.of(error)))
                .isInstanceOf(ExcelOperationException.class)
                .hasMessageNotContaining("password")
                .hasMessageNotContaining("secret");
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {

        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}

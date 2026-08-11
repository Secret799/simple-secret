package com.ss.excel.exporter;

import com.alibaba.excel.annotation.ExcelProperty;
import com.ss.excel.model.ExcelSheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelExporterTest {

    private final ExcelExporter exporter = new ExcelExporter();

    @Test
    void writesModelAndCustomHeadSheets() throws IOException {
        ExcelSheet<UserRow> users = ExcelSheet.<UserRow>builder()
                .name("users")
                .modelType(UserRow.class)
                .rows(List.of(new UserRow(7, "Alice")))
                .build();
        ExcelSheet<List<Object>> custom = ExcelSheet.<List<Object>>builder()
                .name("custom")
                .head(List.of(List.of("Code"), List.of("Description")))
                .rows(List.of(List.of("A-1", "First")))
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        exporter.write(output, List.of(users, custom));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output.toByteArray()))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("users");
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("ID");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("Alice");
            assertThat(workbook.getSheetAt(1).getRow(0).getCell(1).getStringCellValue()).isEqualTo("Description");
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(0).getStringCellValue()).isEqualTo("A-1");
        }
    }

    @Test
    void rejectsMissingOrExcessiveSheets() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThatThrownBy(() -> exporter.write(output, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> exporter.write(output, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sheet");

        List<ExcelSheet<?>> sheets = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            sheets.add(ExcelSheet.<String>builder()
                    .name("sheet-" + index)
                    .modelType(String.class)
                    .build());
        }
        assertThatThrownBy(() -> exporter.write(output, sheets))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    void keepsCallerOutputStreamOpen() {
        TrackingOutputStream output = new TrackingOutputStream();
        ExcelSheet<UserRow> sheet = ExcelSheet.<UserRow>builder()
                .name("users")
                .modelType(UserRow.class)
                .rows(List.of(new UserRow(1, "Alice")))
                .build();

        exporter.write(output, List.of(sheet));

        assertThat(output.closed).isFalse();
        output.write(1);
        assertThat(output.size()).isGreaterThan(1);
    }

    static final class UserRow {

        @ExcelProperty("ID")
        private final int id;

        @ExcelProperty("Name")
        private final String name;

        UserRow(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
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

package com.ss.consumer.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.ss.excel.exporter.ExcelExporter;
import com.ss.excel.importer.ExcelImportRequest;
import com.ss.excel.importer.ExcelImporter;
import com.ss.excel.model.ExcelImportResult;
import com.ss.excel.model.ExcelSheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the published Excel plugin from a third-party application classpath. */
class ExcelPluginConsumerTest {

    @Test
    void roundTripsWorkbookWithoutSpringOrFilesystemAccess() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExcelSheet<UserRow> sheet = ExcelSheet.<UserRow>builder()
                .name("users")
                .modelType(UserRow.class)
                .rows(List.of(new UserRow(7, "Alice")))
                .build();
        new ExcelExporter().write(output, List.of(sheet));

        List<UserRow> imported = new ArrayList<>();
        ExcelImportRequest<UserRow> request = ExcelImportRequest.<UserRow>builder()
                .modelType(UserRow.class)
                .processor((rows, context) -> {
                    imported.addAll(rows);
                    return Map.of();
                })
                .build();
        ExcelImportResult<UserRow> result = new ExcelImporter().read(
                new ByteArrayInputStream(output.toByteArray()), request);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isZero();
        assertThat(imported).extracting(UserRow::getName).containsExactly("Alice");
    }

    public static final class UserRow {

        @ExcelProperty("ID")
        private Integer id;

        @ExcelProperty("Name")
        private String name;

        public UserRow() {
        }

        public UserRow(Integer id, String name) {
            this.id = id;
            this.name = name;
        }

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
}

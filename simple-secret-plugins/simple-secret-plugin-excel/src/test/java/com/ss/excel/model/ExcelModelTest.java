package com.ss.excel.model;

import com.alibaba.excel.write.handler.WriteHandler;
import com.ss.excel.exception.ExcelOperationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelModelTest {

    @Test
    void buildsModelSheetFromDefensiveCopies() {
        List<String> rows = new ArrayList<>(List.of("alice"));
        WriteHandler handler = new WriteHandler() { };

        ExcelSheet<String> sheet = ExcelSheet.<String>builder()
                .name("users")
                .modelType(String.class)
                .rows(rows)
                .addWriteHandler(handler)
                .build();
        rows.add("bob");

        assertThat(sheet.getName()).isEqualTo("users");
        assertThat(sheet.getModelType()).isEqualTo(String.class);
        assertThat(sheet.getHead()).isEmpty();
        assertThat(sheet.getRows()).containsExactly("alice").isUnmodifiable();
        assertThat(sheet.getWriteHandlers()).containsExactly(handler).isUnmodifiable();
    }

    @Test
    void buildsCustomHeadSheetFromDeepCopies() {
        List<String> column = new ArrayList<>(List.of("User", "Name"));
        List<List<String>> head = new ArrayList<>(List.of(column));

        ExcelSheet<List<Object>> sheet = ExcelSheet.<List<Object>>builder()
                .name("custom")
                .head(head)
                .rows(List.of(List.of(1, "Alice")))
                .build();
        column.add("Changed");
        head.clear();

        assertThat(sheet.getModelType()).isNull();
        assertThat(sheet.getHead()).containsExactly(List.of("User", "Name"));
        assertThat(sheet.getHead()).isUnmodifiable();
        assertThat(sheet.getHead().get(0)).isUnmodifiable();
    }

    @Test
    void requiresExactlyOneHeadDefinitionAndValidRows() {
        assertThatThrownBy(() -> ExcelSheet.builder().name("missing").build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("modelType")
                .hasMessageContaining("head");

        assertThatThrownBy(() -> ExcelSheet.<String>builder()
                .name("ambiguous")
                .modelType(String.class)
                .head(List.of(List.of("value")))
                .build())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> ExcelSheet.<String>builder()
                .name("null-row")
                .modelType(String.class)
                .rows(java.util.Arrays.asList("ok", null))
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rows");
    }

    @Test
    void normalizesExcelSheetNameAndRejectsControlCharacters() {
        ExcelSheet<String> sheet = ExcelSheet.<String>builder()
                .name("report/2026:*?[august]-with-a-name-that-is-too-long")
                .modelType(String.class)
                .build();

        assertThat(sheet.getName()).hasSizeLessThanOrEqualTo(31)
                .doesNotContain("/", ":", "*", "?", "[", "]");
        assertThatThrownBy(() -> ExcelSheet.<String>builder()
                .name("bad\u0000name")
                .modelType(String.class)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control");

        ExcelSheet<String> quoted = ExcelSheet.<String>builder()
                .name("'users'")
                .modelType(String.class)
                .build();
        assertThat(quoted.getName()).doesNotStartWith("'").doesNotEndWith("'");
    }

    @Test
    void storesSortedImmutableRowErrors() {
        Map<Integer, String> errors = new LinkedHashMap<>();
        errors.put(3, "invalid name");
        errors.put(1, "missing id");

        ExcelRowError<String> rowError = new ExcelRowError<>(7, "row-value", errors);
        errors.clear();

        assertThat(rowError.getRowNumber()).isEqualTo(7);
        assertThat(rowError.getValue()).isEqualTo("row-value");
        assertThat(rowError.getColumnErrors()).containsExactly(
                Map.entry(1, "missing id"), Map.entry(3, "invalid name"));
        assertThat(rowError.getColumnErrors()).isUnmodifiable();

        assertThatThrownBy(() -> new ExcelRowError<>(0, "row", Map.of(0, "error")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExcelRowError<>(1, "row", Map.of(-1, "error")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesStableImportCountsAndOrderedHeaders() {
        Map<Integer, String> headers = new LinkedHashMap<>();
        headers.put(2, "name");
        headers.put(0, "id");
        ExcelRowError<String> error = new ExcelRowError<>(3, "bad", Map.of(2, "invalid"));

        ExcelImportResult<String> result = new ExcelImportResult<>(4, headers, List.of(error));
        headers.clear();

        assertThat(result.getSuccessCount()).isEqualTo(4);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getTotalCount()).isEqualTo(5);
        assertThat(result.getHeaders()).containsExactly(
                Map.entry(0, "id"), Map.entry(2, "name"));
        assertThat(result.getErrors()).containsExactly(error).isUnmodifiable();

        assertThatThrownBy(() -> new ExcelImportResult<>(-1, Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void operationExceptionDoesNotExposeCauseMessage() {
        RuntimeException cause = new RuntimeException("password=server-secret");

        ExcelOperationException exception =
                new ExcelOperationException("write", "users", cause);

        assertThat(exception.getOperation()).isEqualTo("write");
        assertThat(exception.getSheetName()).isEqualTo("users");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage())
                .contains("write", "users")
                .doesNotContain("server-secret", "password");
    }
}

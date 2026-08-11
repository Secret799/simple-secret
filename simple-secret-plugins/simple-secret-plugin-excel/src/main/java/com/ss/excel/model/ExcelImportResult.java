package com.ss.excel.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Excel 导入的计数、表头和错误行结果。
 *
 * @param <T> 行数据类型
 */
public final class ExcelImportResult<T> {

    private final int successCount;
    private final int failureCount;
    private final int totalCount;
    private final Map<Integer, String> headers;
    private final List<ExcelRowError<T>> errors;

    public ExcelImportResult(int successCount, Map<Integer, String> headers, List<ExcelRowError<T>> errors) {
        if (successCount < 0) {
            throw new IllegalArgumentException("successCount must not be negative");
        }
        Objects.requireNonNull(headers, "headers must not be null");
        Objects.requireNonNull(errors, "errors must not be null");

        TreeMap<Integer, String> sortedHeaders = new TreeMap<>();
        headers.forEach((columnIndex, header) -> {
            if (columnIndex == null || columnIndex < 0) {
                throw new IllegalArgumentException("header column index must not be negative");
            }
            sortedHeaders.put(columnIndex, Objects.requireNonNull(header, "header must not be null"));
        });

        this.successCount = successCount;
        this.errors = List.copyOf(errors);
        this.failureCount = this.errors.size();
        this.totalCount = Math.addExact(successCount, failureCount);
        this.headers = Collections.unmodifiableMap(sortedHeaders);
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public Map<Integer, String> getHeaders() {
        return headers;
    }

    public List<ExcelRowError<T>> getErrors() {
        return errors;
    }
}

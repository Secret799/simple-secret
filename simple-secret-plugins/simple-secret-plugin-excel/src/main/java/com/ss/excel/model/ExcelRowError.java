package com.ss.excel.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 一行 Excel 数据对应的列级校验错误。
 *
 * @param <T> 行数据类型
 */
public final class ExcelRowError<T> {

    private final int rowNumber;
    private final T value;
    private final Map<Integer, String> columnErrors;

    public ExcelRowError(int rowNumber, T value, Map<Integer, String> columnErrors) {
        if (rowNumber < 1) {
            throw new IllegalArgumentException("rowNumber must be greater than zero");
        }
        Objects.requireNonNull(columnErrors, "columnErrors must not be null");
        if (columnErrors.isEmpty()) {
            throw new IllegalArgumentException("columnErrors must not be empty");
        }

        TreeMap<Integer, String> sortedErrors = new TreeMap<>();
        columnErrors.forEach((columnIndex, message) -> {
            if (columnIndex == null || columnIndex < 0) {
                throw new IllegalArgumentException("column index must not be negative");
            }
            sortedErrors.put(columnIndex, Objects.requireNonNull(message, "error message must not be null"));
        });
        this.rowNumber = rowNumber;
        this.value = value;
        this.columnErrors = Collections.unmodifiableMap(sortedErrors);
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public T getValue() {
        return value;
    }

    public Map<Integer, String> getColumnErrors() {
        return columnErrors;
    }
}

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

    /**
     * 数据所在行号。
     */
    private final int rowNumber;
    /**
     * 校验失败的原始行数据。
     */
    private final T value;
    /**
     * 以零基列索引为键的错误信息。
     */
    private final Map<Integer, String> columnErrors;

    /**
     * 创建并初始化实例。
     *
     * @param rowNumber 数据所在行号
     * @param value 校验失败的原始行数据
     * @param columnErrors 以零基列索引为键的错误信息
     */
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

    /**
     * 返回数据所在行号。
     *
     * @return 数据所在行号
     */
    public int getRowNumber() {
        return rowNumber;
    }

    /**
     * 返回校验失败的原始行数据。
     *
     * @return 校验失败的原始行数据
     */
    public T getValue() {
        return value;
    }

    /**
     * 返回以零基列索引为键的错误信息。
     *
     * @return 不可变列错误映射
     */
    public Map<Integer, String> getColumnErrors() {
        return columnErrors;
    }
}

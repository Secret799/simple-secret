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

    /**
     * 成功数量。
     */
    private final int successCount;
    /**
     * 失败数量。
     */
    private final int failureCount;
    /**
     * 结果总数。
     */
    private final int totalCount;
    /**
     * 表头或消息头集合。
     */
    private final Map<Integer, String> headers;
    /**
     * 导入错误列表。
     */
    private final List<ExcelRowError<T>> errors;

    /**
     * 创建并初始化实例。
     *
     * @param successCount 成功数量
     * @param headers 表头或消息头集合
     * @param errors 错误信息列表
     */
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

    /**
     * 返回成功数量。
     *
     * @return 成功数量
     */
    public int getSuccessCount() {
        return successCount;
    }

    /**
     * 返回失败数量。
     *
     * @return 失败数量
     */
    public int getFailureCount() {
        return failureCount;
    }

    /**
     * 返回结果总数。
     *
     * @return 结果总数
     */
    public int getTotalCount() {
        return totalCount;
    }

    /**
     * 返回表头或消息头集合。
     *
     * @return 表头或消息头集合
     */
    public Map<Integer, String> getHeaders() {
        return headers;
    }

    /**
     * 返回错误信息列表。
     *
     * @return 错误信息列表
     */
    public List<ExcelRowError<T>> getErrors() {
        return errors;
    }
}

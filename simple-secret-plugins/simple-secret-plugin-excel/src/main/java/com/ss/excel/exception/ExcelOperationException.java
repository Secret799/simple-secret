package com.ss.excel.exception;

import java.util.Objects;

/**
 * Excel 读取或写入失败时抛出的稳定公共异常。
 */
public final class ExcelOperationException extends RuntimeException {

    private final String operation;
    private final String sheetName;

    /**
     * 创建并初始化实例。
     *
     * @param operation 操作类型
     * @param sheetName 工作表名称
     * @param cause 原始异常
     */
    public ExcelOperationException(String operation, String sheetName, Throwable cause) {
        super(buildMessage(operation, sheetName), cause);
        this.operation = requireText(operation, "operation");
        this.sheetName = requireText(sheetName, "sheetName");
    }

    /**
     * 返回操作类型。
     *
     * @return 操作类型
     */
    public String getOperation() {
        return operation;
    }

    /**
     * 返回工作表名称。
     *
     * @return 工作表名称
     */
    public String getSheetName() {
        return sheetName;
    }

    private static String buildMessage(String operation, String sheetName) {
        return "Excel " + requireText(operation, "operation")
                + " failed for sheet '" + requireText(sheetName, "sheetName") + "'";
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}

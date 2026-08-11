package com.ss.excel.exception;

import java.util.Objects;

/**
 * Excel 读取或写入失败时抛出的稳定公共异常。
 */
public final class ExcelOperationException extends RuntimeException {

    private final String operation;
    private final String sheetName;

    public ExcelOperationException(String operation, String sheetName, Throwable cause) {
        super(buildMessage(operation, sheetName), cause);
        this.operation = requireText(operation, "operation");
        this.sheetName = requireText(sheetName, "sheetName");
    }

    public String getOperation() {
        return operation;
    }

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

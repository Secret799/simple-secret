package com.ss.excel.importer;

import java.util.Map;

/**
 * 当前导入批次的位置与调用方属性。
 */
public final class ExcelBatchContext {

    private final int batchIndex;
    private final int firstRowNumber;
    private final Map<String, Object> attributes;

    ExcelBatchContext(int batchIndex, int firstRowNumber, Map<String, Object> attributes) {
        this.batchIndex = batchIndex;
        this.firstRowNumber = firstRowNumber;
        this.attributes = attributes;
    }

    public int getBatchIndex() {
        return batchIndex;
    }

    public int getFirstRowNumber() {
        return firstRowNumber;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}

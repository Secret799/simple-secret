package com.ss.excel.importer;

import java.util.Map;

/**
 * 当前导入批次的位置与调用方属性。
 */
public final class ExcelBatchContext {

    /**
     * 当前导入批次序号。
     */
    private final int batchIndex;
    /**
     * 首条数据所在行号。
     */
    private final int firstRowNumber;
    /**
     * 扩展属性。
     */
    private final Map<String, Object> attributes;

    ExcelBatchContext(int batchIndex, int firstRowNumber, Map<String, Object> attributes) {
        this.batchIndex = batchIndex;
        this.firstRowNumber = firstRowNumber;
        this.attributes = attributes;
    }

    /**
     * 返回当前导入批次序号。
     *
     * @return 当前导入批次序号
     */
    public int getBatchIndex() {
        return batchIndex;
    }

    /**
     * 返回首条数据所在行号。
     *
     * @return 首条数据所在行号
     */
    public int getFirstRowNumber() {
        return firstRowNumber;
    }

    /**
     * 返回扩展属性。
     *
     * @return 扩展属性
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}

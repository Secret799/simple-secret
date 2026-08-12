package com.ss.excel.importer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 有界批量 Excel 导入配置。
 *
 * @param <T> 行数据类型
 */
public final class ExcelImportRequest<T> {

    public static final int DEFAULT_BATCH_SIZE = 500;
    public static final int DEFAULT_MAX_ROWS = 100_000;

    /**
     * 模型类型。
     */
    private final Class<T> modelType;
    /**
     * 工作表序号。
     */
    private final Integer sheetNo;
    /**
     * 工作表名称。
     */
    private final String sheetName;
    /**
     * 表头行数。
     */
    private final int headRowCount;
    /**
     * 单批处理行数。
     */
    private final int batchSize;
    /**
     * 允许处理的最大行数。
     */
    private final int maxRows;
    /**
     * 扩展属性。
     */
    private final Map<String, Object> attributes;
    /**
     * Excel 批量数据处理器。
     */
    private final ExcelBatchProcessor<T> processor;
    /**
     * 是否填充合并区域的从属单元格。
     */
    private final boolean fillMergedCells;

    private ExcelImportRequest(Builder<T> builder) {
        this.modelType = Objects.requireNonNull(builder.modelType, "modelType must not be null");
        this.sheetNo = builder.sheetNo;
        this.sheetName = normalizeOptionalText(builder.sheetName);
        this.headRowCount = requireNonNegative(builder.headRowCount, "headRowCount");
        this.batchSize = requirePositive(builder.batchSize, "batchSize");
        this.maxRows = requirePositive(builder.maxRows, "maxRows");
        this.attributes = Map.copyOf(builder.attributes);
        this.processor = Objects.requireNonNull(builder.processor, "processor must not be null");
        this.fillMergedCells = builder.fillMergedCells;
        if (sheetName == null && (sheetNo == null || sheetNo < 0)) {
            throw new IllegalArgumentException("sheetNo must not be negative");
        }
    }

    /**
     * 创建构建器。
     *
     * @return 新的构建器
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * 返回模型类型。
     *
     * @return 模型类型
     */
    public Class<T> getModelType() {
        return modelType;
    }

    /**
     * 返回工作表序号。
     *
     * @return 工作表序号
     */
    public Integer getSheetNo() {
        return sheetNo;
    }

    /**
     * 返回工作表名称。
     *
     * @return 工作表名称
     */
    public String getSheetName() {
        return sheetName;
    }

    /**
     * 返回表头行数。
     *
     * @return 表头行数
     */
    public int getHeadRowCount() {
        return headRowCount;
    }

    /**
     * 返回单批处理行数。
     *
     * @return 单批处理行数
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * 返回允许处理的最大行数。
     *
     * @return 允许处理的最大行数
     */
    public int getMaxRows() {
        return maxRows;
    }

    /**
     * 返回扩展属性。
     *
     * @return 扩展属性
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 返回Excel 批量处理器。
     *
     * @return Excel 批量处理器
     */
    public ExcelBatchProcessor<T> getProcessor() {
        return processor;
    }

    /**
     * 判断{@code fillMergedCells}。
     *
     * @return 满足条件时返回 true
     */
    public boolean isFillMergedCells() {
        return fillMergedCells;
    }

    String sheetDescription() {
        return sheetName != null ? sheetName : "#" + sheetNo;
    }

    private static int requirePositive(int value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
        return value;
    }

    private static int requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("sheetName must not be blank");
        }
        return normalized;
    }

    /**
     * {@link ExcelImportRequest} 构建器。
     *
     * @param <T> 行数据类型
     */
    public static final class Builder<T> {

        private Class<T> modelType;
        private Integer sheetNo = 0;
        private String sheetName;
        private int headRowCount = 1;
        private int batchSize = DEFAULT_BATCH_SIZE;
        private int maxRows = DEFAULT_MAX_ROWS;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private ExcelBatchProcessor<T> processor;
        private boolean fillMergedCells;

        private Builder() {
        }

        /**
         * 设置 Excel 行模型类型。
         *
         * @param value 模型类型
         * @return 当前构建器
         */
        public Builder<T> modelType(Class<T> value) {
            this.modelType = value;
            return this;
        }

        /**
         * 设置待导入工作表的零基序号。
         *
         * @param value 工作表序号
         * @return 当前构建器
         */
        public Builder<T> sheetNo(int value) {
            this.sheetNo = value;
            this.sheetName = null;
            return this;
        }

        /**
         * 设置待导入工作表名称。
         *
         * @param value 工作表名称
         * @return 当前构建器
         */
        public Builder<T> sheetName(String value) {
            this.sheetName = value;
            this.sheetNo = null;
            return this;
        }

        /**
         * 设置表头占用行数。
         *
         * @param value 表头行数
         * @return 当前构建器
         */
        public Builder<T> headRowCount(int value) {
            this.headRowCount = value;
            return this;
        }

        /**
         * 设置单批处理行数。
         *
         * @param value 单批处理行数
         * @return 当前构建器
         */
        public Builder<T> batchSize(int value) {
            this.batchSize = value;
            return this;
        }

        /**
         * 设置允许导入的最大数据行数。
         *
         * @param value 允许处理的最大行数
         * @return 当前构建器
         */
        public Builder<T> maxRows(int value) {
            this.maxRows = value;
            return this;
        }

        /**
         * 添加一个导入上下文属性。
         *
         * @param key 属性键
         * @param value 属性值
         * @return 当前构建器
         */
        public Builder<T> attribute(String key, Object value) {
            this.attributes.put(Objects.requireNonNull(key, "attribute key must not be null"),
                    Objects.requireNonNull(value, "attribute value must not be null"));
            return this;
        }

        /**
         * 批量添加导入上下文属性。
         *
         * @param values 输入值集合
         * @return 当前构建器
         */
        public Builder<T> attributes(Map<String, ?> values) {
            Objects.requireNonNull(values, "attributes must not be null");
            values.forEach(this::attribute);
            return this;
        }

        /**
         * 设置批量数据处理器。
         *
         * @param value 批量数据处理器
         * @return 当前构建器
         */
        public Builder<T> processor(ExcelBatchProcessor<T> value) {
            this.processor = value;
            return this;
        }

        /**
         * 设置是否用合并区域首格值填充其余单元格。
         *
         * @param value 是否填充合并单元格
         * @return 当前构建器
         */
        public Builder<T> fillMergedCells(boolean value) {
            this.fillMergedCells = value;
            return this;
        }

        /**
         * 构建结果对象。
         *
         * @return 构建完成的结果对象
         */
        public ExcelImportRequest<T> build() {
            return new ExcelImportRequest<>(this);
        }
    }
}

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

    private final Class<T> modelType;
    private final Integer sheetNo;
    private final String sheetName;
    private final int headRowCount;
    private final int batchSize;
    private final int maxRows;
    private final Map<String, Object> attributes;
    private final ExcelBatchProcessor<T> processor;
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

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public Class<T> getModelType() {
        return modelType;
    }

    public Integer getSheetNo() {
        return sheetNo;
    }

    public String getSheetName() {
        return sheetName;
    }

    public int getHeadRowCount() {
        return headRowCount;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public ExcelBatchProcessor<T> getProcessor() {
        return processor;
    }

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

        public Builder<T> modelType(Class<T> value) {
            this.modelType = value;
            return this;
        }

        public Builder<T> sheetNo(int value) {
            this.sheetNo = value;
            this.sheetName = null;
            return this;
        }

        public Builder<T> sheetName(String value) {
            this.sheetName = value;
            this.sheetNo = null;
            return this;
        }

        public Builder<T> headRowCount(int value) {
            this.headRowCount = value;
            return this;
        }

        public Builder<T> batchSize(int value) {
            this.batchSize = value;
            return this;
        }

        public Builder<T> maxRows(int value) {
            this.maxRows = value;
            return this;
        }

        public Builder<T> attribute(String key, Object value) {
            this.attributes.put(Objects.requireNonNull(key, "attribute key must not be null"),
                    Objects.requireNonNull(value, "attribute value must not be null"));
            return this;
        }

        public Builder<T> attributes(Map<String, ?> values) {
            Objects.requireNonNull(values, "attributes must not be null");
            values.forEach(this::attribute);
            return this;
        }

        public Builder<T> processor(ExcelBatchProcessor<T> value) {
            this.processor = value;
            return this;
        }

        public Builder<T> fillMergedCells(boolean value) {
            this.fillMergedCells = value;
            return this;
        }

        public ExcelImportRequest<T> build() {
            return new ExcelImportRequest<>(this);
        }
    }
}

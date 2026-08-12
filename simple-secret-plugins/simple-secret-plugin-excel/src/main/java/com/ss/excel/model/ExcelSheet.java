package com.ss.excel.model;

import com.alibaba.excel.write.handler.WriteHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一个待写入的 Excel 工作表定义。
 *
 * @param <T> 行数据类型
 */
public final class ExcelSheet<T> {

    private static final int MAX_SHEET_NAME_LENGTH = 31;
    private static final String INVALID_SHEET_NAME_CHARACTERS = "\\/:*?[]";

    /**
     * 名称。
     */
    private final String name;
    /**
     * 模型类型。
     */
    private final Class<T> modelType;
    /**
     * 动态表头列定义。
     */
    private final List<List<String>> head;
    /**
     * 数据行集合。
     */
    private final List<T> rows;
    /**
     * EasyExcel 写处理器列表。
     */
    private final List<WriteHandler> writeHandlers;

    private ExcelSheet(Builder<T> builder) {
        this.name = normalizeSheetName(builder.name);
        this.modelType = builder.modelType;
        this.head = copyHead(builder.head);
        this.rows = copyRows(builder.rows);
        this.writeHandlers = List.copyOf(builder.writeHandlers);
        validateHeadDefinition();
    }

    /**
     * 创建工作表构建器。
     *
     * @param <T> 行数据类型
     * @return 新构建器
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * 返回名称。
     *
     * @return 名称
     */
    public String getName() {
        return name;
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
     * 返回动态表头定义。
     *
     * @return 不可变动态表头定义
     */
    public List<List<String>> getHead() {
        return head;
    }

    /**
     * 返回数据行集合。
     *
     * @return 数据行集合
     */
    public List<T> getRows() {
        return rows;
    }

    /**
     * 返回工作表写处理器。
     *
     * @return 不可变写处理器列表
     */
    public List<WriteHandler> getWriteHandlers() {
        return writeHandlers;
    }

    private void validateHeadDefinition() {
        boolean hasModelType = modelType != null;
        boolean hasCustomHead = !head.isEmpty();
        if (hasModelType == hasCustomHead) {
            throw new IllegalStateException("Exactly one of modelType or head must be configured");
        }
    }

    private static String normalizeSheetName(String value) {
        Objects.requireNonNull(value, "name must not be null");
        String candidate = value.strip();
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        for (int index = 0; index < candidate.length(); index++) {
            if (Character.isISOControl(candidate.charAt(index))) {
                throw new IllegalArgumentException("Sheet name must not contain control characters");
            }
        }

        StringBuilder normalized = new StringBuilder(candidate.length());
        for (int index = 0; index < candidate.length(); index++) {
            char character = candidate.charAt(index);
            normalized.append(INVALID_SHEET_NAME_CHARACTERS.indexOf(character) >= 0 ? '_' : character);
        }
        if (normalized.length() > MAX_SHEET_NAME_LENGTH) {
            normalized.setLength(MAX_SHEET_NAME_LENGTH);
        }
        if (normalized.charAt(0) == '\'') {
            normalized.setCharAt(0, '_');
        }
        if (normalized.charAt(normalized.length() - 1) == '\'') {
            normalized.setCharAt(normalized.length() - 1, '_');
        }
        return normalized.toString();
    }

    private static List<List<String>> copyHead(List<List<String>> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<List<String>> copy = new ArrayList<>(source.size());
        for (int columnIndex = 0; columnIndex < source.size(); columnIndex++) {
            List<String> column = Objects.requireNonNull(
                    source.get(columnIndex), "head column must not be null: " + columnIndex);
            if (column.isEmpty()) {
                throw new IllegalArgumentException("head column must not be empty: " + columnIndex);
            }
            copy.add(List.copyOf(column));
        }
        return List.copyOf(copy);
    }

    private static <T> List<T> copyRows(List<T> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<T> copy = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            copy.add(Objects.requireNonNull(source.get(index), "rows must not contain null: " + index));
        }
        return List.copyOf(copy);
    }

    /**
     * {@link ExcelSheet} 构建器。
     *
     * @param <T> 行数据类型
     */
    public static final class Builder<T> {

        private String name;
        private Class<T> modelType;
        private List<List<String>> head = List.of();
        private List<T> rows = List.of();
        private final List<WriteHandler> writeHandlers = new ArrayList<>();

        private Builder() {
        }

        /**
         * 设置工作表名称。
         *
         * @param value 名称
         * @return 当前构建器
         */
        public Builder<T> name(String value) {
            this.name = value;
            return this;
        }

        /**
         * 设置 EasyExcel 行模型类型。
         *
         * @param value 模型类型
         * @return 当前构建器
         */
        public Builder<T> modelType(Class<T> value) {
            this.modelType = value;
            return this;
        }

        /**
         * 设置动态表头。
         *
         * @param value 动态表头列定义
         * @return 当前构建器
         */
        public Builder<T> head(List<List<String>> value) {
            this.head = value;
            return this;
        }

        /**
         * 设置待写入的数据行。
         *
         * @param value 数据行集合
         * @return 当前构建器
         */
        public Builder<T> rows(List<T> value) {
            this.rows = value;
            return this;
        }

        /**
         * 添加{@code writeHandler}。
         *
         * @param value EasyExcel 写处理器
         * @return 当前构建器
         */
        public Builder<T> addWriteHandler(WriteHandler value) {
            this.writeHandlers.add(Objects.requireNonNull(value, "writeHandler must not be null"));
            return this;
        }

        /**
         * 构建结果对象。
         *
         * @return 构建完成的结果对象
         */
        public ExcelSheet<T> build() {
            return new ExcelSheet<>(this);
        }
    }
}

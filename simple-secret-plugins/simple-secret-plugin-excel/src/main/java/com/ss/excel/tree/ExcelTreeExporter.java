package com.ss.excel.tree;

import com.alibaba.excel.write.handler.WriteHandler;
import com.ss.excel.strategy.ManyRowMergeStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * 将树按根到叶路径展平，并生成父节点列的合并处理器。
 */
public final class ExcelTreeExporter {

    /**
     * 将树按根到叶路径展平，并生成父节点列的合并处理器。
     *
     * @param roots 待导出的根节点列表
     * @param valueExtractors 每层节点使用的列值提取函数
     * @param dataBeginRowIndex 数据区域在工作表中的零基起始行
     * @return 展平的数据行与单元格合并处理器
     */
    public <T> ExcelTreeExport assemble(List<ExcelTreeNode<T>> roots,
                                        List<? extends Function<? super T, ?>> valueExtractors,
                                        int dataBeginRowIndex) {
        Objects.requireNonNull(roots, "roots must not be null");
        Objects.requireNonNull(valueExtractors, "valueExtractors must not be null");
        if (dataBeginRowIndex < 0) {
            throw new IllegalArgumentException("dataBeginRowIndex must not be negative");
        }
        if (valueExtractors.isEmpty()) {
            throw new IllegalArgumentException("At least one value extractor is required");
        }
        List<Function<? super T, ?>> extractors = List.copyOf(valueExtractors);
        for (int index = 0; index < extractors.size(); index++) {
            Objects.requireNonNull(extractors.get(index), "valueExtractors must not contain null: " + index);
        }

        List<List<Object>> rows = new ArrayList<>();
        Map<Integer, List<Integer>> rangesByColumn = new TreeMap<>();
        Set<ExcelTreeNode<T>> path = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ExcelTreeNode<T> root : roots) {
            appendNode(Objects.requireNonNull(root, "roots must not contain null"), 0,
                    dataBeginRowIndex, extractors, rows, rangesByColumn, path);
        }

        List<WriteHandler> handlers = new ArrayList<>(rangesByColumn.size());
        rangesByColumn.forEach((columnIndex, ranges) ->
                handlers.add(new ManyRowMergeStrategy(columnIndex, ranges)));
        return new ExcelTreeExport(rows, handlers);
    }

    private <T> void appendNode(ExcelTreeNode<T> node, int depth, int dataBeginRowIndex,
                                List<Function<? super T, ?>> extractors,
                                List<List<Object>> rows, Map<Integer, List<Integer>> rangesByColumn,
                                Set<ExcelTreeNode<T>> path) {
        if (!path.add(node)) {
            throw new IllegalArgumentException("Tree must not contain cycles");
        }
        int startRow = rows.size();
        int columnOffset = depth * extractors.size();
        if (node.getChildren().isEmpty()) {
            List<Object> row = new ArrayList<>(Collections.nCopies(columnOffset, null));
            appendValues(row, node.getValue(), extractors);
            rows.add(row);
        } else {
            for (ExcelTreeNode<T> child : node.getChildren()) {
                appendNode(child, depth + 1, dataBeginRowIndex, extractors,
                        rows, rangesByColumn, path);
            }
            List<Object> firstRow = rows.get(startRow);
            ensureSize(firstRow, columnOffset + extractors.size());
            for (int index = 0; index < extractors.size(); index++) {
                firstRow.set(columnOffset + index, extractors.get(index).apply(node.getValue()));
            }
            int endRow = rows.size() - 1;
            if (endRow > startRow) {
                for (int index = 0; index < extractors.size(); index++) {
                    rangesByColumn.computeIfAbsent(columnOffset + index, ignored -> new ArrayList<>())
                            .addAll(List.of(dataBeginRowIndex + startRow, dataBeginRowIndex + endRow));
                }
            }
        }
        path.remove(node);
    }

    private <T> void appendValues(List<Object> row, T value,
                                  List<Function<? super T, ?>> extractors) {
        for (Function<? super T, ?> extractor : extractors) {
            row.add(extractor.apply(value));
        }
    }

    private void ensureSize(List<Object> row, int size) {
        while (row.size() < size) {
            row.add(null);
        }
    }
}

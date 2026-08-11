package com.ss.excel.tree;

import java.util.List;
import java.util.Objects;

/**
 * 与业务类型解耦的不可变树节点。
 *
 * @param <T> 节点值类型
 */
public final class ExcelTreeNode<T> {

    private final T value;
    private final List<ExcelTreeNode<T>> children;

    private ExcelTreeNode(T value, List<ExcelTreeNode<T>> children) {
        this.value = Objects.requireNonNull(value, "value must not be null");
        this.children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
    }

    public static <T> ExcelTreeNode<T> of(T value, List<ExcelTreeNode<T>> children) {
        return new ExcelTreeNode<>(value, children);
    }

    public static <T> ExcelTreeNode<T> leaf(T value) {
        return new ExcelTreeNode<>(value, List.of());
    }

    public T getValue() {
        return value;
    }

    public List<ExcelTreeNode<T>> getChildren() {
        return children;
    }
}

package com.ss.excel.tree;

import java.util.List;
import java.util.Objects;

/**
 * 与业务类型解耦的不可变树节点。
 *
 * @param <T> 节点值类型
 */
public final class ExcelTreeNode<T> {

    /**
     * 当前节点承载的业务值。
     */
    private final T value;
    /**
     * 不可变子节点列表。
     */
    private final List<ExcelTreeNode<T>> children;

    private ExcelTreeNode(T value, List<ExcelTreeNode<T>> children) {
        this.value = Objects.requireNonNull(value, "value must not be null");
        this.children = List.copyOf(Objects.requireNonNull(children, "children must not be null"));
    }

    /**
     * 创建包含指定子节点的树节点。
     *
     * @param value 当前节点承载的业务值
     * @param children 子节点列表
     * @return 不可变树节点
     */
    public static <T> ExcelTreeNode<T> of(T value, List<ExcelTreeNode<T>> children) {
        return new ExcelTreeNode<>(value, children);
    }

    /**
     * 创建没有子节点的叶子节点。
     *
     * @param value 叶子节点承载的业务值
     * @return 叶子节点
     */
    public static <T> ExcelTreeNode<T> leaf(T value) {
        return new ExcelTreeNode<>(value, List.of());
    }

    /**
     * 返回当前节点承载的业务值。
     *
     * @return 当前节点承载的业务值
     */
    public T getValue() {
        return value;
    }

    /**
     * 返回不可变子节点列表。
     *
     * @return 不可变子节点列表
     */
    public List<ExcelTreeNode<T>> getChildren() {
        return children;
    }
}

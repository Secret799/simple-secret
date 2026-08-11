package com.ss.common.toolbox.dynamiccolumn;

/**
 * 定义动态列的创建服务。
 *
 * @param <T> 列属性类型
 * @param <D> 列数据类型
 */
public interface IDynamicColumnsService<T extends ColumnProperties, D extends ColumnData> {

    /**
     * 创建动态列。
     *
     * @param column 待创建的列属性
     * @return 创建成功时返回 {@code true}
     */
    boolean createColumn(T column);
}

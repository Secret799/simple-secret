package com.ss.common.toolbox.dynamiccolumn.converter;

/**
 * 定义原始数据与数据库数据之间的双向转换。
 *
 * @param <T> 数据库目标类型
 * @param <R> 原始数据类型
 */
public interface ColumnDataConverter<T, R> {

    /**
     * 将原始数据转换为数据库数据。
     *
     * @param source 原始数据
     * @return 数据库数据
     */
    T ori2db(R source);

    /**
     * 将数据库数据转换为原始数据。
     *
     * @param target 数据库数据
     * @return 原始数据
     */
    R db2ori(T target);
}

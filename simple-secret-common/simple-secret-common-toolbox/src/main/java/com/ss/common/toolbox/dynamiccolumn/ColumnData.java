package com.ss.common.toolbox.dynamiccolumn;

/**
 * 描述动态列所属业务实体的数据标识。
 */
public interface ColumnData {

    /**
     * 获取列标识。
     *
     * @return 列的全局唯一标识
     */
    String columnId();

    /**
     * 获取业务实体标识。
     *
     * @return 业务实体的唯一标识
     */
    String businessId();
}

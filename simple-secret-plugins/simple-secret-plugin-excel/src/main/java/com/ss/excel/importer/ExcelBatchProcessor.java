package com.ss.excel.importer;

import java.util.List;
import java.util.Map;

/**
 * 批量处理导入行并返回列级业务错误。
 *
 * @param <T> 行数据类型
 */
@FunctionalInterface
public interface ExcelBatchProcessor<T> {

    /**
     * 处理一个不可变批次。
     *
     * @param rows 当前批次行
     * @param context 批次上下文
     * @return 外层键为批次内零基行索引，内层键为零基列索引
     */
    Map<Integer, Map<Integer, String>> process(List<T> rows, ExcelBatchContext context);
}

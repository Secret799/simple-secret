package com.ss.excel.tree;

import com.alibaba.excel.write.handler.WriteHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 展平后的树行数据与父节点列合并处理器。
 */
public final class ExcelTreeExport {

    private final List<List<Object>> rows;
    private final List<WriteHandler> writeHandlers;

    ExcelTreeExport(List<List<Object>> rows, List<WriteHandler> writeHandlers) {
        List<List<Object>> rowCopy = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            rowCopy.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        this.rows = Collections.unmodifiableList(rowCopy);
        this.writeHandlers = List.copyOf(writeHandlers);
    }

    /**
     * 返回数据行集合。
     *
     * @return 数据行集合
     */
    public List<List<Object>> getRows() {
        return rows;
    }

    /**
     * 返回树形父节点列使用的合并处理器。
     *
     * @return 不可变写处理器列表
     */
    public List<WriteHandler> getWriteHandlers() {
        return writeHandlers;
    }
}

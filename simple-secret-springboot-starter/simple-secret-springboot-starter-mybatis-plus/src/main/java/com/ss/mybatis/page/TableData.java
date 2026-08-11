package com.ss.mybatis.page;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Objects;

/**
 * 与 HTTP 表达无关的不可变分页结果。
 *
 * @param rows 当前页记录
 * @param total 总记录数
 * @param <T> 记录类型
 */
public record TableData<T>(List<T> rows, long total) {

    /** 创建分页结果并复制记录列表。 */
    public TableData {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (total < 0L) {
            throw new IllegalArgumentException("total must not be negative");
        }
    }

    /**
     * 从 MyBatis-Plus 分页对象创建结果快照。
     *
     * @param page MyBatis-Plus 分页对象
     * @param <T> 记录类型
     * @return 不可变分页结果
     */
    public static <T> TableData<T> from(IPage<T> page) {
        IPage<T> requiredPage = Objects.requireNonNull(page, "page");
        return new TableData<>(requiredPage.getRecords(), requiredPage.getTotal());
    }
}

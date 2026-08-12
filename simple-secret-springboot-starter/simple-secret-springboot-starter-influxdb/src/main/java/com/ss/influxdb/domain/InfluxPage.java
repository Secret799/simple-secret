package com.ss.influxdb.domain;

import java.util.List;

/**
 * 不可变 InfluxDB 分页结果。
 *
 * @param <T> 记录类型
 */
public final class InfluxPage<T> {
    /**
     * 查询结果总条数。
     */
    private final long total;
    /**
     * 当前值。
     */
    private final long current;
    /**
     * 分页大小。
     */
    private final long pageSize;
    /**
     * 查询结果总页数。
     */
    private final long totalPages;
    /**
     * 会话记录集合。
     */
    private final List<T> records;

    /**
     * 创建分页结果并计算总页数。
     *
     * @param total 查询结果总数
     * @param current 当前值
     * @param pageSize 分页大小
     * @param records 会话记录集合
     */
    public InfluxPage(long total, long current, long pageSize, List<T> records) {
        if (total < 0 || current <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("InfluxDB page values are invalid");
        }
        this.total = total;
        this.current = current;
        this.pageSize = pageSize;
        totalPages = total / pageSize + (total % pageSize == 0 ? 0 : 1);
        this.records = records == null ? List.of() : List.copyOf(records);
    }

    /** @return 总记录数 */
    public long getTotal() {
        return total;
    }

    /** @return 当前页，从 1 开始 */
    public long getCurrent() {
        return current;
    }

    /** @return 每页记录数 */
    public long getPageSize() {
        return pageSize;
    }

    /** @return 总页数 */
    public long getTotalPages() {
        return totalPages;
    }

    /** @return 不可变记录列表 */
    public List<T> getRecords() {
        return records;
    }
}

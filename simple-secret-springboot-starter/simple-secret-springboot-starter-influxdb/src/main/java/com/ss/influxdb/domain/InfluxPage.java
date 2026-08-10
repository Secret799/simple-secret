package com.ss.influxdb.domain;

import java.util.List;

/**
 * 不可变 InfluxDB 分页结果。
 *
 * @param <T> 记录类型
 */
public final class InfluxPage<T> {
    private final long total;
    private final long current;
    private final long pageSize;
    private final long totalPages;
    private final List<T> records;

    /** 创建分页结果并计算总页数。 */
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

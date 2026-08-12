package com.ss.ics.hikvision.internal.model;

import java.time.LocalDateTime;

/**
 * 驱动内部使用的录像文件查询结果。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public record HikvisionFileSearchResult(int status, LocalDateTime startTime, LocalDateTime stopTime) {
    public static final int SUCCESS = 1000;
    public static final int NO_FILE = 1001;
    public static final int FINDING = 1002;
    public static final int NO_MORE_FILE = 1003;
    public static final int FILE_EXCEPTION = 1004;
    public static final int FIND_TIMEOUT = 1005;

    /**
     * 创建仍在查询中的状态结果。
     *
     * @return 查询中状态结果
     */
    public static HikvisionFileSearchResult finding() {
        return new HikvisionFileSearchResult(FINDING, null, null);
    }

    /**
     * 创建查询完成状态结果。
     *
     * @return 查询完成状态结果
     */
    public static HikvisionFileSearchResult completed() {
        return new HikvisionFileSearchResult(NO_MORE_FILE, null, null);
    }
}

package com.ss.ics.hikvision.internal.model;

import java.time.LocalDateTime;

/**
 * 驱动内部使用的录像文件查询条件。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public record HikvisionFileSearchCondition(
        int channel,
        int streamType,
        LocalDateTime startTime,
        LocalDateTime stopTime,
        int nativeTimeoutMillis) {
}

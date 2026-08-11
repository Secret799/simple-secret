package com.ss.ics.hikvision;

import java.time.LocalDateTime;

/** 驱动内部使用的录像文件查询条件。 */
record HikvisionFileSearchCondition(
        int channel,
        int streamType,
        LocalDateTime startTime,
        LocalDateTime stopTime,
        int nativeTimeoutMillis) {
}

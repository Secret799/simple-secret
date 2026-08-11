package com.ss.ics.hikvision;

import java.time.LocalDateTime;

/** 驱动内部使用的录像文件查询结果。 */
record HikvisionFileSearchResult(int status, LocalDateTime startTime, LocalDateTime stopTime) {
    static final int SUCCESS = 1000;
    static final int NO_FILE = 1001;
    static final int FINDING = 1002;
    static final int NO_MORE_FILE = 1003;
    static final int FILE_EXCEPTION = 1004;
    static final int FIND_TIMEOUT = 1005;

    static HikvisionFileSearchResult finding() {
        return new HikvisionFileSearchResult(FINDING, null, null);
    }

    static HikvisionFileSearchResult completed() {
        return new HikvisionFileSearchResult(NO_MORE_FILE, null, null);
    }
}

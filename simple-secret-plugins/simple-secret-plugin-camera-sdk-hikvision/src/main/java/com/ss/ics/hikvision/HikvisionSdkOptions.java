package com.ss.ics.hikvision;

import java.nio.file.Path;
import java.time.Duration;

/** 海康 SDK 原生库和执行边界配置。 */
public record HikvisionSdkOptions(
        Path libraryDirectory,
        Duration fileSearchTimeout,
        int asyncPtzQueueCapacity) {

    /** 默认录像查询超时。 */
    public static final Duration DEFAULT_FILE_SEARCH_TIMEOUT = Duration.ofSeconds(5);
    /** 默认异步 PTZ 队列容量。 */
    public static final int DEFAULT_ASYNC_PTZ_QUEUE_CAPACITY = 256;

    /**
     * 校验并规范化配置。
     *
     * @param libraryDirectory 厂商 SDK 动态库目录
     * @param fileSearchTimeout 录像检索超时时间
     * @param asyncPtzQueueCapacity 异步云台任务队列容量
     */
    public HikvisionSdkOptions {
        if (libraryDirectory == null) {
            throw new IllegalArgumentException("libraryDirectory must not be null");
        }
        if (fileSearchTimeout == null
                || fileSearchTimeout.compareTo(Duration.ofSeconds(5)) < 0) {
            throw new IllegalArgumentException(
                    "fileSearchTimeout must be at least 5 seconds");
        }
        if (asyncPtzQueueCapacity <= 0 || asyncPtzQueueCapacity == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("asyncPtzQueueCapacity must be positive");
        }
        libraryDirectory = libraryDirectory.toAbsolutePath().normalize();
    }

    /**
     * @param libraryDirectory 厂商 SDK 原生库目录
     * @return 使用安全默认值的配置
     */
    public static HikvisionSdkOptions defaults(Path libraryDirectory) {
        return new HikvisionSdkOptions(
                libraryDirectory,
                DEFAULT_FILE_SEARCH_TIMEOUT,
                DEFAULT_ASYNC_PTZ_QUEUE_CAPACITY);
    }
}

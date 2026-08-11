package com.ss.ics.dahua;

import java.nio.file.Path;
import java.time.Duration;

/** 大华 SDK 原生库、执行超时和资源上限配置。 */
public record DahuaSdkOptions(
        Path libraryDirectory,
        Duration operationTimeout,
        Duration radiometrySearchTimeout,
        int asyncPtzQueueCapacity,
        int maxRadiometryResults) {

    /** 默认单次原生操作超时。 */
    public static final Duration DEFAULT_OPERATION_TIMEOUT = Duration.ofSeconds(3);
    /** 默认热成像历史查询超时。 */
    public static final Duration DEFAULT_RADIOMETRY_SEARCH_TIMEOUT = Duration.ofSeconds(5);
    /** 默认异步 PTZ 队列容量。 */
    public static final int DEFAULT_ASYNC_PTZ_QUEUE_CAPACITY = 256;
    /** 默认单次历史热成像查询结果上限。 */
    public static final int DEFAULT_MAX_RADIOMETRY_RESULTS = 10_000;
    /** 可配置的异步 PTZ 队列容量上限。 */
    public static final int MAX_ASYNC_PTZ_QUEUE_CAPACITY = 10_000;
    /** 可配置的历史热成像结果上限。 */
    public static final int MAX_RADIOMETRY_RESULTS = 100_000;

    /** 校验并规范化配置。 */
    public DahuaSdkOptions {
        if (libraryDirectory == null) {
            throw new IllegalArgumentException("libraryDirectory must not be null");
        }
        if (operationTimeout == null || operationTimeout.isZero()
                || operationTimeout.isNegative()) {
            throw new IllegalArgumentException("operationTimeout must be positive");
        }
        validateNativeTimeout(operationTimeout, "operationTimeout");
        if (radiometrySearchTimeout == null || radiometrySearchTimeout.isZero()
                || radiometrySearchTimeout.isNegative()) {
            throw new IllegalArgumentException("radiometrySearchTimeout must be positive");
        }
        validateNativeTimeout(radiometrySearchTimeout, "radiometrySearchTimeout");
        if (asyncPtzQueueCapacity <= 0) {
            throw new IllegalArgumentException("asyncPtzQueueCapacity must be positive");
        }
        if (asyncPtzQueueCapacity > MAX_ASYNC_PTZ_QUEUE_CAPACITY) {
            throw new IllegalArgumentException(
                    "asyncPtzQueueCapacity must not exceed " + MAX_ASYNC_PTZ_QUEUE_CAPACITY);
        }
        if (maxRadiometryResults <= 0) {
            throw new IllegalArgumentException("maxRadiometryResults must be positive");
        }
        if (maxRadiometryResults > MAX_RADIOMETRY_RESULTS) {
            throw new IllegalArgumentException(
                    "maxRadiometryResults must not exceed " + MAX_RADIOMETRY_RESULTS);
        }
        libraryDirectory = libraryDirectory.toAbsolutePath().normalize();
    }

    private static void validateNativeTimeout(Duration timeout, String name) {
        if (timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException(
                    name + " must not exceed " + Integer.MAX_VALUE + " milliseconds");
        }
    }

    /**
     * @param libraryDirectory 厂商 SDK 原生库目录
     * @return 使用安全默认值的配置
     */
    public static DahuaSdkOptions defaults(Path libraryDirectory) {
        return new DahuaSdkOptions(
                libraryDirectory,
                DEFAULT_OPERATION_TIMEOUT,
                DEFAULT_RADIOMETRY_SEARCH_TIMEOUT,
                DEFAULT_ASYNC_PTZ_QUEUE_CAPACITY,
                DEFAULT_MAX_RADIOMETRY_RESULTS);
    }
}

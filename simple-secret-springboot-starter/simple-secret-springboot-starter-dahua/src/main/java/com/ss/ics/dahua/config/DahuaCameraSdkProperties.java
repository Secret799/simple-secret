package com.ss.ics.dahua.config;

import com.ss.ics.dahua.DahuaSdkOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/** 大华 Camera SDK 配置。 */
@ConfigurationProperties(prefix = "simple-secret.camera-sdk.dahua")
public class DahuaCameraSdkProperties {

    /** 是否初始化大华原生 SDK。 */
    private boolean enabled;

    /** NetSDK 动态库所在目录。 */
    private Path libraryDirectory;

    /** 登录、PTZ 和实时预览等原生操作超时时间。 */
    private Duration operationTimeout = DahuaSdkOptions.DEFAULT_OPERATION_TIMEOUT;

    /** 历史热成像记录检索的总超时时间。 */
    private Duration radiometrySearchTimeout =
            DahuaSdkOptions.DEFAULT_RADIOMETRY_SEARCH_TIMEOUT;

    /** 异步 PTZ 单线程执行器的等待队列容量。 */
    private int asyncPtzQueueCapacity = DahuaSdkOptions.DEFAULT_ASYNC_PTZ_QUEUE_CAPACITY;

    /** 单次历史热成像查询允许返回的最大记录数。 */
    private int maxRadiometryResults = DahuaSdkOptions.DEFAULT_MAX_RADIOMETRY_RESULTS;

    /**
     * 转换为纯 Java SDK 配置并执行最终校验。
     *
     * @return 大华 SDK 配置
     */
    public DahuaSdkOptions toOptions() {
        if (libraryDirectory == null) {
            throw new IllegalStateException(
                    "simple-secret.camera-sdk.dahua.library-directory must be configured");
        }
        return new DahuaSdkOptions(
                libraryDirectory,
                operationTimeout,
                radiometrySearchTimeout,
                asyncPtzQueueCapacity,
                maxRadiometryResults);
    }

    /** @return 是否初始化大华原生 SDK */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否初始化大华原生 SDK */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return NetSDK 动态库目录 */
    public Path getLibraryDirectory() {
        return libraryDirectory;
    }

    /** @param libraryDirectory NetSDK 动态库目录 */
    public void setLibraryDirectory(Path libraryDirectory) {
        this.libraryDirectory = libraryDirectory;
    }

    /** @return 原生操作超时时间 */
    public Duration getOperationTimeout() {
        return operationTimeout;
    }

    /** @param operationTimeout 原生操作超时时间 */
    public void setOperationTimeout(Duration operationTimeout) {
        this.operationTimeout = operationTimeout;
    }

    /** @return 历史热成像记录检索总超时时间 */
    public Duration getRadiometrySearchTimeout() {
        return radiometrySearchTimeout;
    }

    /** @param radiometrySearchTimeout 历史热成像记录检索总超时时间 */
    public void setRadiometrySearchTimeout(Duration radiometrySearchTimeout) {
        this.radiometrySearchTimeout = radiometrySearchTimeout;
    }

    /** @return 异步 PTZ 队列容量 */
    public int getAsyncPtzQueueCapacity() {
        return asyncPtzQueueCapacity;
    }

    /** @param asyncPtzQueueCapacity 异步 PTZ 队列容量 */
    public void setAsyncPtzQueueCapacity(int asyncPtzQueueCapacity) {
        this.asyncPtzQueueCapacity = asyncPtzQueueCapacity;
    }

    /** @return 历史热成像查询结果上限 */
    public int getMaxRadiometryResults() {
        return maxRadiometryResults;
    }

    /** @param maxRadiometryResults 历史热成像查询结果上限 */
    public void setMaxRadiometryResults(int maxRadiometryResults) {
        this.maxRadiometryResults = maxRadiometryResults;
    }
}

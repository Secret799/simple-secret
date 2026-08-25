package com.ss.ics.hikvision.config;

import com.ss.ics.hikvision.HikvisionSdkOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/** 海康威视 Camera SDK 配置。 */
@ConfigurationProperties(prefix = "simple-secret.camera-sdk.hikvision")
public class HikvisionCameraSdkProperties {

    /** 是否初始化海康威视原生 SDK。 */
    private boolean enabled;

    /** HCNetSDK 动态库所在目录。 */
    private Path libraryDirectory;

    /** 单次录像检索的总超时时间。 */
    private Duration fileSearchTimeout = HikvisionSdkOptions.DEFAULT_FILE_SEARCH_TIMEOUT;

    /** 异步 PTZ 单线程执行器的等待队列容量。 */
    private int asyncPtzQueueCapacity = HikvisionSdkOptions.DEFAULT_ASYNC_PTZ_QUEUE_CAPACITY;

    /**
     * 转换为纯 Java SDK 配置并执行最终校验。
     *
     * @return 海康威视 SDK 配置
     */
    public HikvisionSdkOptions toOptions() {
        if (libraryDirectory == null) {
            throw new IllegalStateException(
                    "simple-secret.camera-sdk.hikvision.library-directory must be configured");
        }
        return new HikvisionSdkOptions(
                libraryDirectory, fileSearchTimeout, asyncPtzQueueCapacity);
    }

    /** @return 是否初始化海康威视原生 SDK */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否初始化海康威视原生 SDK */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return HCNetSDK 动态库目录 */
    public Path getLibraryDirectory() {
        return libraryDirectory;
    }

    /** @param libraryDirectory HCNetSDK 动态库目录 */
    public void setLibraryDirectory(Path libraryDirectory) {
        this.libraryDirectory = libraryDirectory;
    }

    /** @return 录像检索总超时时间 */
    public Duration getFileSearchTimeout() {
        return fileSearchTimeout;
    }

    /** @param fileSearchTimeout 录像检索总超时时间 */
    public void setFileSearchTimeout(Duration fileSearchTimeout) {
        this.fileSearchTimeout = fileSearchTimeout;
    }

    /** @return 异步 PTZ 队列容量 */
    public int getAsyncPtzQueueCapacity() {
        return asyncPtzQueueCapacity;
    }

    /** @param asyncPtzQueueCapacity 异步 PTZ 队列容量 */
    public void setAsyncPtzQueueCapacity(int asyncPtzQueueCapacity) {
        this.asyncPtzQueueCapacity = asyncPtzQueueCapacity;
    }
}

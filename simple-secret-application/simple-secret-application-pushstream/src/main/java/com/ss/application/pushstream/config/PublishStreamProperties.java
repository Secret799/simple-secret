package com.ss.application.pushstream.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 本地媒体文件推流应用配置。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@Validated
@ConfigurationProperties(prefix = "simple-secret.publish-stream")
public class PublishStreamProperties {

    /** 是否启用目录扫描和 FFmpeg 推流。 */
    private boolean enabled;

    /** 是否启用只读状态接口。 */
    private boolean statusApiEnabled;

    /** 待扫描的绝对媒体目录。 */
    @NotNull
    private Path scanDirectory = Path.of(System.getProperty("java.io.tmpdir"), "simple-secret-publishstream")
            .toAbsolutePath().normalize();

    /** 允许扫描的小写媒体后缀。 */
    @NotEmpty
    private Set<@NotBlank @Pattern(regexp = "[A-Za-z0-9]{1,16}") String> allowedSuffixes = new LinkedHashSet<>(
            Set.of("mp4", "mov", "mkv", "ts", "flv", "h264", "h265"));

    /** ZLMediaKit 应用名。 */
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
    private String app = "publish";

    /** FFmpeg 可执行文件或绝对路径。 */
    @NotBlank
    private String ffmpegExecutable = "ffmpeg";

    /** ZLMediaKit RTSP 主机，只应指向本机或可信内网地址。 */
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,255}")
    private String rtspHost = "127.0.0.1";

    /** ZLMediaKit RTSP 端口。 */
    @Min(1)
    @Max(65535)
    private int rtspPort = 7554;

    /** 两次扫描之间的固定延迟。 */
    @NotNull
    private Duration scanInterval = Duration.ofSeconds(30);

    /** 停止 FFmpeg 进程时的优雅退出等待时间。 */
    @NotNull
    private Duration shutdownTimeout = Duration.ofSeconds(5);

    /** 最大并发 FFmpeg 推流进程数。 */
    @Min(1)
    @Max(128)
    private int maxConcurrentStreams = 8;

    /** 单次扫描最多接受的媒体文件数。 */
    @Min(1)
    @Max(10000)
    private int maxScannedFiles = 1000;

    /** 是否递归扫描子目录。 */
    private boolean recursive;

    /**
     * 校验启用时扫描目录必须是已存在的绝对普通目录。
     *
     * @return 配置可安全使用时返回 true
     */
    @AssertTrue(message = "scan-directory must be an existing absolute directory when publish stream is enabled")
    public boolean isScanDirectoryValid() {
        if (!enabled) {
            return true;
        }
        if (scanDirectory == null) {
            return false;
        }
        Path normalizedDirectory = scanDirectory.toAbsolutePath().normalize();
        return scanDirectory.isAbsolute() && Files.isDirectory(normalizedDirectory)
                && !Files.isSymbolicLink(normalizedDirectory);
    }

    /**
     * 校验持续时间范围，防止忙循环或长时间阻塞停机。
     *
     * @return 持续时间合法时返回 true
     */
    @AssertTrue(message = "scan-interval and shutdown-timeout are outside the allowed range")
    public boolean areDurationsValid() {
        return isWithin(scanInterval, Duration.ofSeconds(1), Duration.ofHours(1))
                && isWithin(shutdownTimeout, Duration.ofMillis(100), Duration.ofMinutes(1));
    }

    private boolean isWithin(Duration value, Duration minimum, Duration maximum) {
        return value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }

    /** @return 是否启用推流 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用推流 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 是否启用状态接口 */
    public boolean isStatusApiEnabled() {
        return statusApiEnabled;
    }

    /** @param statusApiEnabled 是否启用状态接口 */
    public void setStatusApiEnabled(boolean statusApiEnabled) {
        this.statusApiEnabled = statusApiEnabled;
    }

    /** @return 扫描目录 */
    public Path getScanDirectory() {
        return scanDirectory;
    }

    /** @param scanDirectory 扫描目录 */
    public void setScanDirectory(Path scanDirectory) {
        this.scanDirectory = scanDirectory;
    }

    /** @return 允许文件后缀 */
    public Set<String> getAllowedSuffixes() {
        return allowedSuffixes;
    }

    /** @param allowedSuffixes 允许文件后缀 */
    public void setAllowedSuffixes(Set<String> allowedSuffixes) {
        this.allowedSuffixes = allowedSuffixes;
    }

    /** @return ZLMediaKit 应用名 */
    public String getApp() {
        return app;
    }

    /** @param app ZLMediaKit 应用名 */
    public void setApp(String app) {
        this.app = app;
    }

    /** @return FFmpeg 可执行文件 */
    public String getFfmpegExecutable() {
        return ffmpegExecutable;
    }

    /** @param ffmpegExecutable FFmpeg 可执行文件 */
    public void setFfmpegExecutable(String ffmpegExecutable) {
        this.ffmpegExecutable = ffmpegExecutable;
    }

    /** @return RTSP 主机 */
    public String getRtspHost() {
        return rtspHost;
    }

    /** @param rtspHost RTSP 主机 */
    public void setRtspHost(String rtspHost) {
        this.rtspHost = rtspHost;
    }

    /** @return RTSP 端口 */
    public int getRtspPort() {
        return rtspPort;
    }

    /** @param rtspPort RTSP 端口 */
    public void setRtspPort(int rtspPort) {
        this.rtspPort = rtspPort;
    }

    /** @return 扫描间隔 */
    public Duration getScanInterval() {
        return scanInterval;
    }

    /** @param scanInterval 扫描间隔 */
    public void setScanInterval(Duration scanInterval) {
        this.scanInterval = scanInterval;
    }

    /** @return 进程停止等待时间 */
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    /** @param shutdownTimeout 进程停止等待时间 */
    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    /** @return 最大并发推流数 */
    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    /** @param maxConcurrentStreams 最大并发推流数 */
    public void setMaxConcurrentStreams(int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    /** @return 单次扫描最大文件数 */
    public int getMaxScannedFiles() {
        return maxScannedFiles;
    }

    /** @param maxScannedFiles 单次扫描最大文件数 */
    public void setMaxScannedFiles(int maxScannedFiles) {
        this.maxScannedFiles = maxScannedFiles;
    }

    /** @return 是否递归扫描 */
    public boolean isRecursive() {
        return recursive;
    }

    /** @param recursive 是否递归扫描 */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }
}

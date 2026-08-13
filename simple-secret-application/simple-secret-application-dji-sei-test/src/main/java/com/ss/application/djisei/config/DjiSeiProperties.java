package com.ss.application.djisei.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * DJI RTMP SEI 诊断配置。
 *
 * @author junpzx
 * @since 2026-08-13
 */
@Validated
@ConfigurationProperties(prefix = "simple-secret.dji-sei")
public class DjiSeiProperties {

    /** 单帧最大允许 64 MiB。 */
    private static final int MAX_FRAME_BYTES = 64 * 1024 * 1024;

    /** 单条 SEI 负载最大允许 64 MiB，并受单帧上限进一步约束。 */
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;

    /** 单条日志预览最大允许 4096 字节。 */
    private static final int MAX_PREVIEW_BYTES = 4096;

    /** 汇总间隔下限。 */
    private static final Duration MIN_SUMMARY_INTERVAL = Duration.ofSeconds(1);

    /** 汇总间隔上限。 */
    private static final Duration MAX_SUMMARY_INTERVAL = Duration.ofHours(1);

    /** 是否启用 DJI SEI 诊断。 */
    private boolean enabled;

    /** 允许诊断的 ZLMediaKit 应用名。 */
    @NotBlank
    @Size(max = 64)
    private String allowedApp = "live";

    /** 单个视频帧允许的最大字节数。 */
    @Min(1)
    @Max(MAX_FRAME_BYTES)
    private int maxFrameBytes = 8 * 1024 * 1024;

    /** 单条 SEI 负载允许的最大字节数。 */
    @Min(1)
    @Max(MAX_PAYLOAD_BYTES)
    private int maxPayloadBytes = 1024 * 1024;

    /** 单条 SEI 日志允许预览的最大字节数。 */
    @Min(1)
    @Max(MAX_PREVIEW_BYTES)
    private int previewBytes = 64;

    /** 同一媒体流周期汇总的时间间隔。 */
    @NotNull
    private Duration summaryInterval = Duration.ofSeconds(30);

    /**
     * 校验负载上限不超过单帧上限。
     *
     * @return 负载上限为正数且不超过单帧上限时返回 true
     */
    @AssertTrue(message = "max-payload-bytes must not exceed max-frame-bytes")
    public boolean isPayloadLimitWithinFrameLimit() {
        return maxPayloadBytes > 0 && maxPayloadBytes <= maxFrameBytes;
    }

    /**
     * 校验周期汇总间隔处于允许范围。
     *
     * @return 汇总间隔位于 1 秒到 1 小时的闭区间时返回 true
     */
    @AssertTrue(message = "summary-interval must be between 1 second and 1 hour")
    public boolean isSummaryIntervalWithinRange() {
        return summaryInterval != null
                && summaryInterval.compareTo(MIN_SUMMARY_INTERVAL) >= 0
                && summaryInterval.compareTo(MAX_SUMMARY_INTERVAL) <= 0;
    }

    /** @return 是否启用 DJI SEI 诊断 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用 DJI SEI 诊断 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 允许诊断的应用名 */
    public String getAllowedApp() {
        return allowedApp;
    }

    /** @param allowedApp 允许诊断的应用名 */
    public void setAllowedApp(String allowedApp) {
        this.allowedApp = allowedApp;
    }

    /** @return 单帧最大字节数 */
    public int getMaxFrameBytes() {
        return maxFrameBytes;
    }

    /** @param maxFrameBytes 单帧最大字节数 */
    public void setMaxFrameBytes(int maxFrameBytes) {
        this.maxFrameBytes = maxFrameBytes;
    }

    /** @return 单条 SEI 负载最大字节数 */
    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    /** @param maxPayloadBytes 单条 SEI 负载最大字节数 */
    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    /** @return 单条日志预览最大字节数 */
    public int getPreviewBytes() {
        return previewBytes;
    }

    /** @param previewBytes 单条日志预览最大字节数 */
    public void setPreviewBytes(int previewBytes) {
        this.previewBytes = previewBytes;
    }

    /** @return 周期汇总间隔 */
    public Duration getSummaryInterval() {
        return summaryInterval;
    }

    /** @param summaryInterval 周期汇总间隔 */
    public void setSummaryInterval(Duration summaryInterval) {
        this.summaryInterval = summaryInterval;
    }
}

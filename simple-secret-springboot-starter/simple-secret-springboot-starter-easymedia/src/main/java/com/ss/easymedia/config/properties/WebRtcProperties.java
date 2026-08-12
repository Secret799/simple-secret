package com.ss.easymedia.config.properties;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.net.URI;
import java.time.Duration;

/**
 * WebRTC 会话网关配置。
 */
public class WebRtcProperties {

    /**
     * 是否启用 WebRTC 会话网关。
     */
    private boolean enabled = true;

    /**
     * 是否直接使用当前服务内嵌的 ZLM C API 完成 WebRTC 信令。
     */
    private boolean localZlmEnabled = false;

    /**
     * 内嵌 ZLM 原生 WHIP/WHEP HTTP 地址。
     */
    private URI signalingBaseUrl = URI.create("http://127.0.0.1:7080");

    /**
     * 连接 ZLM 超时时间。
     */
    private Duration connectTimeout = Duration.ofSeconds(2);

    /**
     * ZLM 信令请求读取超时时间。
     */
    private Duration requestTimeout = Duration.ofSeconds(8);

    /**
     * 活跃会话 Redis 存活时间。
     */
    private Duration sessionTtl = Duration.ofHours(1);

    /**
     * 关闭中会话 Redis 存活时间。
     */
    private Duration closingTtl = Duration.ofMinutes(5);

    /**
     * 关闭补偿任务执行间隔。
     */
    private Duration cleanupInterval = Duration.ofSeconds(10);

    /**
     * 关闭补偿任务首次执行延迟。
     */
    private Duration cleanupInitialDelay = Duration.ofSeconds(30);

    /**
     * 单次关闭补偿最大处理数。
     */
    private int cleanupBatchSize = 100;

    /**
     * SDP 最大字节数。
     */
    private int maxSdpBytes = 65536;

    /**
     * 上游是否支持 WHIP/WHEP Trickle ICE PATCH。
     * 当前内嵌 ZLMediaKit 不支持，默认关闭。
     */
    private boolean trickleIceEnabled = false;

    /**
     * 对外会话资源路径前缀。
     */
    private String publicSessionBasePath = "/easyMedia/api/webrtc/sessions";

    /**
     * 安全配置。
     */
    @NestedConfigurationProperty
    private WebRtcSecurityProperties security = new WebRtcSecurityProperties();

    /**
     * 限流配置。
     */
    @NestedConfigurationProperty
    private WebRtcRateLimitProperties rateLimit = new WebRtcRateLimitProperties();

    /**
     * 判断是否启用。
     *
     * @return 满足条件时返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 判断{@code localZlmEnabled}。
     *
     * @return 满足条件时返回 true
     */
    public boolean isLocalZlmEnabled() {
        return localZlmEnabled;
    }

    /**
     * 设置{@code localZlmEnabled}。
     *
     * @param localZlmEnabled 是否使用内嵌 ZLMediaKit
     */
    public void setLocalZlmEnabled(boolean localZlmEnabled) {
        this.localZlmEnabled = localZlmEnabled;
    }

    /**
     * 返回ZLM WebRTC 信令基础地址。
     *
     * @return ZLM WebRTC 信令基础地址
     */
    public URI getSignalingBaseUrl() {
        return signalingBaseUrl;
    }

    /**
     * 设置{@code signalingBaseUrl}。
     *
     * @param signalingBaseUrl ZLM WebRTC 信令基础地址
     */
    public void setSignalingBaseUrl(URI signalingBaseUrl) {
        this.signalingBaseUrl = signalingBaseUrl;
    }

    /**
     * 返回连接超时时间。
     *
     * @return 连接超时时间
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * 设置{@code connectTimeout}。
     *
     * @param connectTimeout 连接超时时间
     */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * 返回请求超时时间。
     *
     * @return 请求超时时间
     */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * 设置{@code requestTimeout}。
     *
     * @param requestTimeout 请求超时时间
     */
    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    /**
     * 返回活动会话有效期。
     *
     * @return 活动会话有效期
     */
    public Duration getSessionTtl() {
        return sessionTtl;
    }

    /**
     * 设置{@code sessionTtl}。
     *
     * @param sessionTtl 活动会话有效期
     */
    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    /**
     * 返回关闭中会话的缓存有效期。
     *
     * @return 关闭中会话的缓存有效期
     */
    public Duration getClosingTtl() {
        return closingTtl;
    }

    /**
     * 设置{@code closingTtl}。
     *
     * @param closingTtl 关闭中会话的缓存有效期
     */
    public void setClosingTtl(Duration closingTtl) {
        this.closingTtl = closingTtl;
    }

    /**
     * 返回会话清理周期。
     *
     * @return 会话清理周期
     */
    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    /**
     * 设置{@code cleanupInterval}。
     *
     * @param cleanupInterval 会话清理周期
     */
    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    /**
     * 返回首次清理延迟。
     *
     * @return 首次清理延迟
     */
    public Duration getCleanupInitialDelay() {
        return cleanupInitialDelay;
    }

    /**
     * 设置{@code cleanupInitialDelay}。
     *
     * @param cleanupInitialDelay 首次清理延迟
     */
    public void setCleanupInitialDelay(Duration cleanupInitialDelay) {
        this.cleanupInitialDelay = cleanupInitialDelay;
    }

    /**
     * 返回单次清理的最大会话数。
     *
     * @return 单次清理的最大会话数
     */
    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    /**
     * 设置{@code cleanupBatchSize}。
     *
     * @param cleanupBatchSize 单次清理的最大会话数
     */
    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

    /**
     * 返回SDP 最大字节数。
     *
     * @return SDP 最大字节数
     */
    public int getMaxSdpBytes() {
        return maxSdpBytes;
    }

    /**
     * 设置{@code maxSdpBytes}。
     *
     * @param maxSdpBytes SDP 最大字节数
     */
    public void setMaxSdpBytes(int maxSdpBytes) {
        this.maxSdpBytes = maxSdpBytes;
    }

    /**
     * 判断{@code trickleIceEnabled}。
     *
     * @return 满足条件时返回 true
     */
    public boolean isTrickleIceEnabled() {
        return trickleIceEnabled;
    }

    /**
     * 设置{@code trickleIceEnabled}。
     *
     * @param trickleIceEnabled 是否代理 Trickle ICE PATCH
     */
    public void setTrickleIceEnabled(boolean trickleIceEnabled) {
        this.trickleIceEnabled = trickleIceEnabled;
    }

    /**
     * 返回对外 WebRTC 会话基础路径。
     *
     * @return 对外 WebRTC 会话基础路径
     */
    public String getPublicSessionBasePath() {
        return publicSessionBasePath;
    }

    /**
     * 设置{@code publicSessionBasePath}。
     *
     * @param publicSessionBasePath 对外 WebRTC 会话基础路径
     */
    public void setPublicSessionBasePath(String publicSessionBasePath) {
        this.publicSessionBasePath = publicSessionBasePath;
    }

    /**
     * 返回WebRTC 安全配置。
     *
     * @return WebRTC 安全配置
     */
    public WebRtcSecurityProperties getSecurity() {
        return security;
    }

    /**
     * 设置{@code security}。
     *
     * @param security WebRTC 安全配置
     */
    public void setSecurity(WebRtcSecurityProperties security) {
        this.security = security;
    }

    /**
     * 返回WebRTC 限流配置。
     *
     * @return WebRTC 限流配置
     */
    public WebRtcRateLimitProperties getRateLimit() {
        return rateLimit;
    }

    /**
     * 设置{@code rateLimit}。
     *
     * @param rateLimit WebRTC 限流配置
     */
    public void setRateLimit(WebRtcRateLimitProperties rateLimit) {
        this.rateLimit = rateLimit;
    }
}

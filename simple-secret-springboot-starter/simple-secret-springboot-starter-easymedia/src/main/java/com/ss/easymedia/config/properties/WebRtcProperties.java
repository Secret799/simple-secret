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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLocalZlmEnabled() {
        return localZlmEnabled;
    }

    public void setLocalZlmEnabled(boolean localZlmEnabled) {
        this.localZlmEnabled = localZlmEnabled;
    }

    public URI getSignalingBaseUrl() {
        return signalingBaseUrl;
    }

    public void setSignalingBaseUrl(URI signalingBaseUrl) {
        this.signalingBaseUrl = signalingBaseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public Duration getClosingTtl() {
        return closingTtl;
    }

    public void setClosingTtl(Duration closingTtl) {
        this.closingTtl = closingTtl;
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    public Duration getCleanupInitialDelay() {
        return cleanupInitialDelay;
    }

    public void setCleanupInitialDelay(Duration cleanupInitialDelay) {
        this.cleanupInitialDelay = cleanupInitialDelay;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public int getMaxSdpBytes() {
        return maxSdpBytes;
    }

    public void setMaxSdpBytes(int maxSdpBytes) {
        this.maxSdpBytes = maxSdpBytes;
    }

    public boolean isTrickleIceEnabled() {
        return trickleIceEnabled;
    }

    public void setTrickleIceEnabled(boolean trickleIceEnabled) {
        this.trickleIceEnabled = trickleIceEnabled;
    }

    public String getPublicSessionBasePath() {
        return publicSessionBasePath;
    }

    public void setPublicSessionBasePath(String publicSessionBasePath) {
        this.publicSessionBasePath = publicSessionBasePath;
    }

    public WebRtcSecurityProperties getSecurity() {
        return security;
    }

    public void setSecurity(WebRtcSecurityProperties security) {
        this.security = security;
    }

    public WebRtcRateLimitProperties getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(WebRtcRateLimitProperties rateLimit) {
        this.rateLimit = rateLimit;
    }
}

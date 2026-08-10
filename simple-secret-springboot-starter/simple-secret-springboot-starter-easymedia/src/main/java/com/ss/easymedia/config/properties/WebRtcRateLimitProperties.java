package com.ss.easymedia.config.properties;

import java.time.Duration;

/**
 * WebRTC 信令限流配置。
 */
public class WebRtcRateLimitProperties {

    /**
     * 是否启用限流。
     */
    private boolean enabled = true;

    /**
     * 每分钟允许的推流会话创建数。
     */
    private int publishPerMinute = 60;

    /**
     * 每分钟允许的播放会话创建数。
     */
    private int playPerMinute = 120;

    /**
     * 每分钟允许的会话 PATCH/DELETE 操作数。
     */
    private int sessionOperationPerMinute = 300;

    /**
     * 限流键存活时间。
     */
    private Duration keyTtl = Duration.ofMinutes(10);

    /**
     * 单机内存限流器允许保留的最大键数量。
     */
    private int localMaxKeys = 10000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPublishPerMinute() {
        return publishPerMinute;
    }

    public void setPublishPerMinute(int publishPerMinute) {
        this.publishPerMinute = publishPerMinute;
    }

    public int getPlayPerMinute() {
        return playPerMinute;
    }

    public void setPlayPerMinute(int playPerMinute) {
        this.playPerMinute = playPerMinute;
    }

    public int getSessionOperationPerMinute() {
        return sessionOperationPerMinute;
    }

    public void setSessionOperationPerMinute(int sessionOperationPerMinute) {
        this.sessionOperationPerMinute = sessionOperationPerMinute;
    }

    public Duration getKeyTtl() {
        return keyTtl;
    }

    public void setKeyTtl(Duration keyTtl) {
        this.keyTtl = keyTtl;
    }

    public int getLocalMaxKeys() {
        return localMaxKeys;
    }

    public void setLocalMaxKeys(int localMaxKeys) {
        this.localMaxKeys = localMaxKeys;
    }
}

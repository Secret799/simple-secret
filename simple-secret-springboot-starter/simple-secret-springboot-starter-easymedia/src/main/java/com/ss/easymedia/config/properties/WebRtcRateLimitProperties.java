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
     * 返回每主体每分钟推流会话创建上限。
     *
     * @return 每主体每分钟推流会话创建上限
     */
    public int getPublishPerMinute() {
        return publishPerMinute;
    }

    /**
     * 设置{@code publishPerMinute}。
     *
     * @param publishPerMinute 每主体每分钟推流会话创建上限
     */
    public void setPublishPerMinute(int publishPerMinute) {
        this.publishPerMinute = publishPerMinute;
    }

    /**
     * 返回每主体每分钟播放会话创建上限。
     *
     * @return 每主体每分钟播放会话创建上限
     */
    public int getPlayPerMinute() {
        return playPerMinute;
    }

    /**
     * 设置{@code playPerMinute}。
     *
     * @param playPerMinute 每主体每分钟播放会话创建上限
     */
    public void setPlayPerMinute(int playPerMinute) {
        this.playPerMinute = playPerMinute;
    }

    /**
     * 返回每主体每分钟会话操作上限。
     *
     * @return 每主体每分钟会话操作上限
     */
    public int getSessionOperationPerMinute() {
        return sessionOperationPerMinute;
    }

    /**
     * 设置{@code sessionOperationPerMinute}。
     *
     * @param sessionOperationPerMinute 每主体每分钟会话操作上限
     */
    public void setSessionOperationPerMinute(int sessionOperationPerMinute) {
        this.sessionOperationPerMinute = sessionOperationPerMinute;
    }

    /**
     * 返回限流键有效期。
     *
     * @return 限流键有效期
     */
    public Duration getKeyTtl() {
        return keyTtl;
    }

    /**
     * 设置{@code keyTtl}。
     *
     * @param keyTtl 限流键有效期
     */
    public void setKeyTtl(Duration keyTtl) {
        this.keyTtl = keyTtl;
    }

    /**
     * 返回本地限流器最大键数量。
     *
     * @return 本地限流器最大键数量
     */
    public int getLocalMaxKeys() {
        return localMaxKeys;
    }

    /**
     * 设置{@code localMaxKeys}。
     *
     * @param localMaxKeys 本地限流器最大键数量
     */
    public void setLocalMaxKeys(int localMaxKeys) {
        this.localMaxKeys = localMaxKeys;
    }
}

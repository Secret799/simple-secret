package com.ss.easymedia.webrtc.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * Redis 中保存的 WebRTC 会话映射。
 */
public class WebRtcSessionRecord implements Serializable {

    /** Redis 会话快照序列化兼容版本。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 对外暴露的随机会话标识。 */
    private String sessionId;
    /** 会话用途，决定推流或播放鉴权及限流策略。 */
    private WebRtcSessionType sessionType;
    /** 当前生命周期状态。 */
    private WebRtcSessionState state;
    /** 创建会话的租户标识。 */
    private String tenantId;
    /** 创建会话的认证主体标识。 */
    private String subject;
    /** ZLM 应用名。 */
    private String app;
    /** ZLM 流名。 */
    private String stream;
    /** 持有媒体 transport 的节点标识。 */
    private String mediaNodeId;
    /** 仅供网关内部调用的上游会话资源地址。 */
    private String upstreamLocation;
    /** 上游 PATCH 所需的最新 ETag。 */
    private String upstreamEtag;
    /** 会话创建时间戳，单位毫秒。 */
    private long createdAt;
    /** 最近一次状态变更时间戳，单位毫秒。 */
    private long updatedAt;
    /** 下一次 DELETE 补偿执行时间戳，单位毫秒。 */
    private long nextDeleteRetryAt;
    /** 上游 DELETE 的已尝试次数。 */
    private int deleteRetryCount;

    /**
     * 创建并初始化实例。
     */
    public WebRtcSessionRecord() {
    }

    /**
     * 创建并初始化实例。
     *
     * @param sessionId 会话 ID
     * @param sessionType WebRTC 会话类型
     * @param state 会话状态
     * @param tenantId 租户标识
     * @param subject NATS subject
     * @param app 媒体应用名
     * @param stream 媒体流标识
     * @param mediaNodeId 媒体节点标识
     * @param upstreamLocation 上游会话 Location
     * @param upstreamEtag 上游会话 ETag
     * @param createdAt 会话创建时间
     * @param updatedAt 会话更新时间
     * @param nextDeleteRetryAt 下一次上游删除重试时间
     * @param deleteRetryCount 上游会话删除重试次数
     */
    public WebRtcSessionRecord(String sessionId, WebRtcSessionType sessionType, WebRtcSessionState state,
                               String tenantId, String subject, String app, String stream,
                               String mediaNodeId, String upstreamLocation, String upstreamEtag,
                               long createdAt, long updatedAt, long nextDeleteRetryAt, int deleteRetryCount) {
        this.sessionId = sessionId;
        this.sessionType = sessionType;
        this.state = state;
        this.tenantId = tenantId;
        this.subject = subject;
        this.app = app;
        this.stream = stream;
        this.mediaNodeId = mediaNodeId;
        this.upstreamLocation = upstreamLocation;
        this.upstreamEtag = upstreamEtag;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.nextDeleteRetryAt = nextDeleteRetryAt;
        this.deleteRetryCount = deleteRetryCount;
    }

    /**
     * 返回会话 ID。
     *
     * @return 会话 ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 设置会话 ID。
     *
     * @param sessionId 会话 ID
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 返回WebRTC 会话类型。
     *
     * @return WebRTC 会话类型
     */
    public WebRtcSessionType getSessionType() {
        return sessionType;
    }

    /**
     * 设置{@code sessionType}。
     *
     * @param sessionType WebRTC 会话类型
     */
    public void setSessionType(WebRtcSessionType sessionType) {
        this.sessionType = sessionType;
    }

    /**
     * 返回会话状态。
     *
     * @return 会话状态
     */
    public WebRtcSessionState getState() {
        return state;
    }

    /**
     * 设置{@code state}。
     *
     * @param state 会话状态
     */
    public void setState(WebRtcSessionState state) {
        this.state = state;
    }

    /**
     * 返回租户标识。
     *
     * @return 租户标识
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 设置{@code tenantId}。
     *
     * @param tenantId 租户标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * 返回NATS subject。
     *
     * @return NATS subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * 设置NATS subject。
     *
     * @param subject NATS subject
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    /**
     * 返回媒体应用名。
     *
     * @return 媒体应用名
     */
    public String getApp() {
        return app;
    }

    /**
     * 设置{@code app}。
     *
     * @param app 媒体应用名
     */
    public void setApp(String app) {
        this.app = app;
    }

    /**
     * 返回媒体流标识。
     *
     * @return 媒体流标识
     */
    public String getStream() {
        return stream;
    }

    /**
     * 设置{@code stream}。
     *
     * @param stream 媒体流标识
     */
    public void setStream(String stream) {
        this.stream = stream;
    }

    /**
     * 返回媒体节点标识。
     *
     * @return 媒体节点标识
     */
    public String getMediaNodeId() {
        return mediaNodeId;
    }

    /**
     * 设置{@code mediaNodeId}。
     *
     * @param mediaNodeId 媒体节点标识
     */
    public void setMediaNodeId(String mediaNodeId) {
        this.mediaNodeId = mediaNodeId;
    }

    /**
     * 返回上游会话 Location。
     *
     * @return 上游会话 Location
     */
    public String getUpstreamLocation() {
        return upstreamLocation;
    }

    /**
     * 设置上游会话 Location。
     *
     * @param upstreamLocation 上游会话 Location
     */
    public void setUpstreamLocation(String upstreamLocation) {
        this.upstreamLocation = upstreamLocation;
    }

    /**
     * 返回上游会话 ETag。
     *
     * @return 上游会话 ETag
     */
    public String getUpstreamEtag() {
        return upstreamEtag;
    }

    /**
     * 设置{@code upstreamEtag}。
     *
     * @param upstreamEtag 上游会话 ETag
     */
    public void setUpstreamEtag(String upstreamEtag) {
        this.upstreamEtag = upstreamEtag;
    }

    /**
     * 返回会话创建时间。
     *
     * @return 会话创建时间
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置{@code createdAt}。
     *
     * @param createdAt 会话创建时间
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 返回会话更新时间。
     *
     * @return 会话更新时间
     */
    public long getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置{@code updatedAt}。
     *
     * @param updatedAt 会话更新时间
     */
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 返回下一次上游删除重试时间。
     *
     * @return 下一次上游删除重试时间
     */
    public long getNextDeleteRetryAt() {
        return nextDeleteRetryAt;
    }

    /**
     * 设置{@code nextDeleteRetryAt}。
     *
     * @param nextDeleteRetryAt 下一次上游删除重试时间
     */
    public void setNextDeleteRetryAt(long nextDeleteRetryAt) {
        this.nextDeleteRetryAt = nextDeleteRetryAt;
    }

    /**
     * 返回上游会话删除重试次数。
     *
     * @return 上游会话删除重试次数
     */
    public int getDeleteRetryCount() {
        return deleteRetryCount;
    }

    /**
     * 设置{@code deleteRetryCount}。
     *
     * @param deleteRetryCount 上游会话删除重试次数
     */
    public void setDeleteRetryCount(int deleteRetryCount) {
        this.deleteRetryCount = deleteRetryCount;
    }
}

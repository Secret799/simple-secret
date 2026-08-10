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

    public WebRtcSessionRecord() {
    }

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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public WebRtcSessionType getSessionType() {
        return sessionType;
    }

    public void setSessionType(WebRtcSessionType sessionType) {
        this.sessionType = sessionType;
    }

    public WebRtcSessionState getState() {
        return state;
    }

    public void setState(WebRtcSessionState state) {
        this.state = state;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public String getMediaNodeId() {
        return mediaNodeId;
    }

    public void setMediaNodeId(String mediaNodeId) {
        this.mediaNodeId = mediaNodeId;
    }

    public String getUpstreamLocation() {
        return upstreamLocation;
    }

    public void setUpstreamLocation(String upstreamLocation) {
        this.upstreamLocation = upstreamLocation;
    }

    public String getUpstreamEtag() {
        return upstreamEtag;
    }

    public void setUpstreamEtag(String upstreamEtag) {
        this.upstreamEtag = upstreamEtag;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getNextDeleteRetryAt() {
        return nextDeleteRetryAt;
    }

    public void setNextDeleteRetryAt(long nextDeleteRetryAt) {
        this.nextDeleteRetryAt = nextDeleteRetryAt;
    }

    public int getDeleteRetryCount() {
        return deleteRetryCount;
    }

    public void setDeleteRetryCount(int deleteRetryCount) {
        this.deleteRetryCount = deleteRetryCount;
    }
}

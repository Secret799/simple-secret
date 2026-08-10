package com.ss.easymedia.webrtc.domain;

/**
 * WebRTC HTTP 会话类型。
 */
public enum WebRtcSessionType {
    /** WHIP 发布会话，对应 ZLM push 信令。 */
    WHIP("/index/api/whip", WebRtcOperation.PUBLISH),
    /** WHEP 播放会话，对应 ZLM play 信令。 */
    WHEP("/index/api/whep", WebRtcOperation.PLAY);

    /** 外置 ZLM HTTP 信令端点路径。 */
    private final String upstreamPath;
    /** 创建该会话时执行的网关操作。 */
    private final WebRtcOperation operation;

    WebRtcSessionType(String upstreamPath, WebRtcOperation operation) {
        this.upstreamPath = upstreamPath;
        this.operation = operation;
    }

    public String getUpstreamPath() {
        return upstreamPath;
    }

    public WebRtcOperation getOperation() {
        return operation;
    }
}

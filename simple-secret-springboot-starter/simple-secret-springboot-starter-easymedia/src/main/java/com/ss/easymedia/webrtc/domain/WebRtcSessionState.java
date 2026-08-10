package com.ss.easymedia.webrtc.domain;

/**
 * WebRTC 会话状态。
 */
public enum WebRtcSessionState {
    /** 会话可正常进行 PATCH、DELETE 操作。 */
    ACTIVE,
    /** 已请求关闭，等待上游删除成功或补偿重试。 */
    CLOSING
}

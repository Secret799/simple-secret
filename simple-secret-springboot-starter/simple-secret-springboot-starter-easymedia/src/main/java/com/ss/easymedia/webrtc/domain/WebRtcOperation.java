package com.ss.easymedia.webrtc.domain;

/**
 * WebRTC 网关操作。
 */
public enum WebRtcOperation {
    /** 创建 WHIP 推流会话。 */
    PUBLISH,
    /** 创建 WHEP 播放会话。 */
    PLAY,
    /** 更新既有会话的 ICE 信息。 */
    PATCH,
    /** 关闭既有会话。 */
    DELETE
}

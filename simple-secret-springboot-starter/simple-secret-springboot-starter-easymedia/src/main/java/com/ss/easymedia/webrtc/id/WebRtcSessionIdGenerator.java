package com.ss.easymedia.webrtc.id;

/**
 * WebRTC 对外会话 ID 生成器。
 */
public interface WebRtcSessionIdGenerator {

    /**
     * 生成不可预测的公开会话标识。
     *
     * @return 新的会话标识
     */
    String generate();
}

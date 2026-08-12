package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.webrtc.domain.WebRtcIdentity;
import com.ss.easymedia.webrtc.domain.WebRtcSessionRecord;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;

/**
 * WebRTC 发布、播放和会话操作访问策略。
 */
public interface WebRtcAccessPolicy {

    /**
     * 校验调用方能否为指定流创建会话。

     *
     * @param identity 调用方身份
     * @param type 目标类型
     * @param app 媒体应用名
     * @param stream 媒体流标识
     */
    void authorizeCreate(WebRtcIdentity identity, WebRtcSessionType type, String app, String stream);

    /**
     * 校验调用方能否操作既有会话。

     *
     * @param identity 调用方身份
     * @param record 会话记录
     */
    void authorizeSession(WebRtcIdentity identity, WebRtcSessionRecord record);
}

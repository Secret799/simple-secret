package com.ss.easymedia.webrtc.security;

import com.ss.easymedia.webrtc.domain.WebRtcIdentity;

/**
 * 解析当前 WebRTC 请求身份。
 */
public interface WebRtcIdentityProvider {

    /**
     * 解析当前请求身份。
     *
     * @return 租户、主体和认证状态

     *
     * @param clientIp 客户端 IP 地址
     */
    WebRtcIdentity current(String clientIp);
}

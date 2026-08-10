package com.ss.easymedia.webrtc.domain;

import org.springframework.http.MediaType;

/**
 * WebRTC HTTP 协议媒体类型。
 */
public final class WebRtcMediaTypes {

    /** SDP Offer/Answer 的标准媒体类型字符串。 */
    public static final String APPLICATION_SDP_VALUE = "application/sdp";
    /** SDP Offer/Answer 的 Spring 媒体类型对象。 */
    public static final MediaType APPLICATION_SDP = MediaType.parseMediaType(APPLICATION_SDP_VALUE);
    /** Trickle ICE SDP Fragment 的标准媒体类型字符串。 */
    public static final String TRICKLE_ICE_SDPFRAG_VALUE = "application/trickle-ice-sdpfrag";
    /** Trickle ICE SDP Fragment 的 Spring 媒体类型对象。 */
    public static final MediaType TRICKLE_ICE_SDPFRAG = MediaType.parseMediaType(TRICKLE_ICE_SDPFRAG_VALUE);

    /** 禁止实例化协议常量类。 */
    private WebRtcMediaTypes() {
    }
}

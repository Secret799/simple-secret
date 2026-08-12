package com.ss.easymedia.controller;

import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.domain.WebRtcGatewayResponse;
import com.ss.easymedia.webrtc.domain.WebRtcMediaTypes;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import com.ss.easymedia.webrtc.service.WebRtcSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WHIP/WHEP 会话网关。
 */
@RestController
@RequestMapping("/easyMedia/api/webrtc")
@ConditionalOnProperty(prefix = "simple-secret.easymedia.webrtc", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class Zlm4jWebRTCController {

    /** 执行会话创建、更新和关闭的应用服务。 */
    private final WebRtcSessionService sessionService;
    /** 读取 SDP 大小、本地 ZLM 模式和 Trickle ICE 配置。 */
    private final WebRtcProperties properties;

    /**
     * 创建并初始化实例。
     *
     * @param sessionService WebRTC 会话服务
     * @param properties 模块配置
     */
    public Zlm4jWebRTCController(WebRtcSessionService sessionService, WebRtcProperties properties) {
        this.sessionService = sessionService;
        this.properties = properties;
    }

    /**
     * 接收 WHIP SDP Offer 并返回 ZLM SDP Answer。
     *
     * @return SDP Answer；仅受管 HTTP 信令模式携带会话 Location

     *
     * @param app 媒体应用名
     * @param stream 媒体流标识
     * @param headers 表头或消息头集合
     * @param request 请求对象
     */
    @PostMapping("/whip")
    public ResponseEntity<byte[]> whip(@RequestParam String app,
                                       @RequestParam String stream,
                                       @RequestHeader HttpHeaders headers,
                                       HttpServletRequest request) {
        requireContentType(headers, WebRtcMediaTypes.APPLICATION_SDP);
        byte[] body = readSdpBody(request);
        return toResponse(sessionService.create(WebRtcSessionType.WHIP, app, stream,
                headers, body, request.getRemoteAddr()));
    }

    /**
     * 接收 WHEP SDP Offer 并返回 ZLM SDP Answer。
     *
     * @return SDP Answer；仅受管 HTTP 信令模式携带会话 Location

     *
     * @param app 媒体应用名
     * @param stream 媒体流标识
     * @param headers 表头或消息头集合
     * @param request 请求对象
     */
    @PostMapping("/whep")
    public ResponseEntity<byte[]> whep(@RequestParam String app,
                                       @RequestParam String stream,
                                       @RequestHeader HttpHeaders headers,
                                       HttpServletRequest request) {
        requireContentType(headers, WebRtcMediaTypes.APPLICATION_SDP);
        byte[] body = readSdpBody(request);
        return toResponse(sessionService.create(WebRtcSessionType.WHEP, app, stream,
                headers, body, request.getRemoteAddr()));
    }

    /**
     * 向外置 ZLM 的受管会话转发 Trickle ICE SDP Fragment。
     *
     * @return 上游 PATCH 响应

     *
     * @param sessionId 会话 ID
     * @param headers 表头或消息头集合
     * @param request 请求对象
     */
    @PatchMapping("/sessions/{sessionId}")
    public ResponseEntity<byte[]> patch(@PathVariable String sessionId,
                                        @RequestHeader HttpHeaders headers,
                                        HttpServletRequest request) {
        requireManagedSessionOperations();
        if (!properties.isTrickleIceEnabled()) {
            throw new WebRtcSessionException(HttpStatus.METHOD_NOT_ALLOWED,
                    "WEBRTC_TRICKLE_ICE_UNSUPPORTED",
                    "The configured ZLM upstream does not support Trickle ICE PATCH");
        }
        requireContentType(headers, WebRtcMediaTypes.TRICKLE_ICE_SDPFRAG);
        byte[] body = readSdpBody(request);
        return toResponse(sessionService.patch(
                sessionId, headers, body, request.getRemoteAddr()));
    }

    /**
     * 请求关闭一个受管 WebRTC 会话。
     *
     * @return 无内容响应；删除失败将进入后台补偿

     *
     * @param sessionId 会话 ID
     * @param request 请求对象
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> delete(@PathVariable String sessionId,
                                       HttpServletRequest request) {
        requireManagedSessionOperations();
        sessionService.delete(sessionId, request.getRemoteAddr());
        return ResponseEntity.noContent()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .build();
    }

    /**
     * 旧接口没有可管理的会话资源，明确停止使用。
     */
    @Deprecated
    @PostMapping("/sdp")
    public void legacySdp() {
        throw legacyApiRemoved();
    }

    /**
     * 旧删除接口无法绑定真实 transport，明确停止使用。
     */
    @Deprecated
    @DeleteMapping("/deleteWebrtc")
    public void legacyDelete() {
        throw legacyApiRemoved();
    }

    /**
     * 在受配置字节限制保护下读取 SDP 请求体。
     *
     * @return 已验证大小的 SDP 字节内容
     */
    private byte[] readSdpBody(HttpServletRequest request) {
        int maxBytes = properties.getMaxSdpBytes();
        if (maxBytes <= 0) {
            throw WebRtcSessionException.serviceUnavailable("WEBRTC_SDP_LIMIT_INVALID");
        }
        if (request.getContentLengthLong() > maxBytes) {
            throw payloadTooLarge();
        }
        try {
            int readLimit = maxBytes == Integer.MAX_VALUE ? maxBytes : maxBytes + 1;
            byte[] body = request.getInputStream().readNBytes(readLimit);
            if (body.length > maxBytes) {
                throw payloadTooLarge();
            }
            return body;
        } catch (java.io.IOException exception) {
            throw WebRtcSessionException.badRequest(
                    "WEBRTC_SDP_READ_FAILED", "Unable to read SDP request body");
        }
    }

    /**
     * 创建统一的 SDP 体积超限异常。
     */
    private WebRtcSessionException payloadTooLarge() {
        return new WebRtcSessionException(HttpStatus.PAYLOAD_TOO_LARGE,
                "WEBRTC_SDP_TOO_LARGE", "SDP body exceeds configured limit");
    }

    /**
     * 校验请求 Content-Type 与协议所需的媒体类型兼容。
     */
    private void requireContentType(HttpHeaders headers, MediaType expected) {
        MediaType actual = headers.getContentType();
        if (actual == null || !expected.isCompatibleWith(actual)) {
            throw new WebRtcSessionException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "WEBRTC_CONTENT_TYPE_UNSUPPORTED",
                    "Expected Content-Type " + expected);
        }
    }

    /**
     * 将领域网关响应转换为禁用缓存的 Spring HTTP 响应。
     */
    private ResponseEntity<byte[]> toResponse(WebRtcGatewayResponse response) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.headers());
        headers.setCacheControl("no-store");
        return ResponseEntity.status(response.status())
                .headers(headers)
                .body(response.body());
    }

    /**
     * 创建指向新 WHIP/WHEP 生命周期接口的旧接口下线异常。
     */
    private WebRtcSessionException legacyApiRemoved() {
        return new WebRtcSessionException(HttpStatus.GONE,
                "WEBRTC_LEGACY_SESSION_API_REMOVED",
                "Use /whip, /whep and /sessions/{sessionId}");
    }

    /**
     * 拒绝内嵌 ZLM 模式下没有上游 HTTP 会话资源的 PATCH 和 DELETE 请求。
     */
    private void requireManagedSessionOperations() {
        if (properties.isLocalZlmEnabled()) {
            throw new WebRtcSessionException(HttpStatus.METHOD_NOT_ALLOWED,
                    "WEBRTC_LOCAL_ZLM_SESSION_OPERATION_UNSUPPORTED",
                    "Embedded ZLM signaling does not expose managed session operations");
        }
    }
}

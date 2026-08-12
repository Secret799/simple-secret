package com.ss.easymedia.webrtc.client;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/**
 * 限制 ZLM 会话资源 URI 只能指向受信任的内部信令服务。
 */
public class ZlmWebRtcUriPolicy {

    /** ZLM HTTP API 的受信会话资源路径前缀。 */
    private static final String ALLOWED_PATH_PREFIX = "/index/api/";

    /** 经过规范化并作为信任边界的 ZLM 信令服务地址。 */
    private final URI signalingBaseUri;

    /**
     * 创建上游会话资源 URI 校验策略。

     *
     * @param signalingBaseUri ZLM 信令基础 URI
     */
    public ZlmWebRtcUriPolicy(URI signalingBaseUri) {
        this.signalingBaseUri = normalizeBase(signalingBaseUri);
    }

    /**
     * 将上游 Location 解析为绝对 URI，并确认其仍属于配置的 ZLM 服务。
     *
     * @return 可用于后续信令调用的受信绝对 URI

     *
     * @param requestUri 请求 URI
     * @param location 上游会话 Location
     */
    public URI requireTrustedLocation(URI requestUri, URI location) {
        Objects.requireNonNull(requestUri, "requestUri");
        Objects.requireNonNull(location, "location");
        URI resolved = requestUri.resolve(location).normalize();
        if (resolved.getScheme() == null || resolved.getHost() == null) {
            throw new IllegalArgumentException("ZLM Location must resolve to an absolute HTTP URI");
        }
        if (resolved.getUserInfo() != null || resolved.getFragment() != null) {
            throw new IllegalArgumentException("ZLM Location contains forbidden URI components");
        }
        if (!sameAuthority(signalingBaseUri, resolved)) {
            throw new IllegalArgumentException("ZLM Location points outside the trusted signaling service");
        }
        String rawPath = Objects.requireNonNullElse(resolved.getRawPath(), "");
        if (!rawPath.startsWith(ALLOWED_PATH_PREFIX)
                || rawPath.toLowerCase(Locale.ROOT).contains("%2e")) {
            throw new IllegalArgumentException("ZLM Location path is not allowed");
        }
        return resolved;
    }

    /**
     * 验证并规范化配置的 HTTP(S) 信令服务基地址。
     */
    private static URI normalizeBase(URI uri) {
        Objects.requireNonNull(uri, "signalingBaseUri");
        URI normalized = uri.normalize();
        if (normalized.getScheme() == null || normalized.getHost() == null
                || normalized.getUserInfo() != null || normalized.getFragment() != null) {
            throw new IllegalArgumentException("Invalid ZLM signaling base URI");
        }
        String scheme = normalized.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("ZLM signaling base URI must use HTTP or HTTPS");
        }
        return normalized;
    }

    /**
     * 判断两个 URI 是否使用相同的协议、主机和有效端口。
     */
    private static boolean sameAuthority(URI expected, URI actual) {
        return expected.getScheme().equalsIgnoreCase(actual.getScheme())
                && expected.getHost().equalsIgnoreCase(actual.getHost())
                && effectivePort(expected) == effectivePort(actual);
    }

    /**
     * 返回 URI 的显式端口，或按协议推导默认端口。
     */
    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}

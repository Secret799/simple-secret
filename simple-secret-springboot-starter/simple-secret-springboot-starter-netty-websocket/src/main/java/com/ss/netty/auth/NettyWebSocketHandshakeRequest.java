package com.ss.netty.auth;

import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * WebSocket 握手请求的不可变快照。
 *
 * <p>{@link #toString()} 不包含 header、查询参数、cookie 或完整 URI，避免凭据进入日志。</p>
 */
public final class NettyWebSocketHandshakeRequest {

    /**
     * 握手请求 HTTP 方法。
     */
    private final String method;
    /**
     * 握手请求 URI。
     */
    private final String uri;
    /**
     * 文件或资源路径。
     */
    private final String path;
    /**
     * 表头或消息头集合。
     */
    private final Map<String, List<String>> headers;
    /**
     * 握手查询参数快照。
     */
    private final Map<String, List<String>> queryParameters;
    /**
     * 握手请求 Cookie 快照。
     */
    private final Map<String, String> cookies;
    /**
     * 握手客户端远端地址。
     */
    private final SocketAddress remoteAddress;

    /**
     * 创建并校验握手请求快照。
     *
     * @param method HTTP 方法
     * @param uri 请求 URI
     * @param path 文件或资源路径
     * @param headers 表头或消息头集合
     * @param queryParameters 握手查询参数
     * @param cookies 握手请求 Cookie 快照
     * @param remoteAddress 客户端远端地址
     */
    public NettyWebSocketHandshakeRequest(String method, String uri, String path,
                                          Map<String, List<String>> headers,
                                          Map<String, List<String>> queryParameters,
                                          Map<String, String> cookies,
                                          SocketAddress remoteAddress) {
        this.method = requireText(method, "method");
        this.uri = requireText(uri, "uri");
        this.path = requirePath(path);
        this.headers = immutableMultiMap(headers, true, "headers");
        this.queryParameters = immutableMultiMap(queryParameters, false, "queryParameters");
        this.cookies = immutableStringMap(cookies, "cookies");
        this.remoteAddress = remoteAddress;
    }

    /**
     * 返回 HTTP 方法。
     *
     * @return 返回的 {@code String} 结果
     */
    public String method() {
        return method;
    }

    /**
     * 返回原始请求 URI。该值可能包含敏感查询参数，不应直接记录。
     *
     * @return 返回的 {@code String} 结果
     */
    public String uri() {
        return uri;
    }

    /**
     * 返回不含查询参数的端点路径。
     *
     * @return 返回的 {@code String} 结果
     */
    public String path() {
        return path;
    }

    /**
     * 返回已深度复制的只读 header；名称统一为小写。
     *
     * @return 返回的 {@code Map<String, List<String>>} 结果
     */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /**
     * 返回已深度复制的只读查询参数。
     *
     * @return 返回的 {@code Map<String, List<String>>} 结果
     */
    public Map<String, List<String>> queryParameters() {
        return queryParameters;
    }

    /**
     * 返回只读 cookie 映射。
     *
     * @return 返回的 {@code Map<String, String>} 结果
     */
    public Map<String, String> cookies() {
        return cookies;
    }

    /**
     * 返回远端地址；嵌入式通道等场景可能为空。
     *
     * @return 返回的 {@code Optional<SocketAddress>} 结果
     */
    public Optional<SocketAddress> remoteAddress() {
        return Optional.ofNullable(remoteAddress);
    }

    /**
     * 按大小写不敏感的名称返回第一个 header 值。
     *
     * @param name 名称
     * @return 返回的 {@code Optional<String>} 结果
     */
    public Optional<String> firstHeader(String name) {
        return first(headers.get(normalizedName(name, "header name")));
    }

    /**
     * 返回指定查询参数的第一个值。
     *
     * @param name 名称
     * @return 返回的 {@code Optional<String>} 结果
     */
    public Optional<String> firstQueryParameter(String name) {
        return first(queryParameters.get(requireText(name, "query parameter name")));
    }

    /**
     * 返回指定 cookie 值。
     *
     * @param name 名称
     * @return 返回的 {@code Optional<String>} 结果
     */
    public Optional<String> cookie(String name) {
        return Optional.ofNullable(cookies.get(requireText(name, "cookie name")));
    }

    /** 返回不含凭据和消息内容的安全摘要。 */
    @Override
    public String toString() {
        return "NettyWebSocketHandshakeRequest[method=" + method
                + ", path=" + path + ", remoteAddress=" + remoteAddress + ']';
    }

    private static Optional<String> first(List<String> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private static Map<String, List<String>> immutableMultiMap(
            Map<String, List<String>> source, boolean normalizeKeys, String name) {
        Objects.requireNonNull(source, name + " must not be null");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((key, values) -> {
            String validKey = normalizeKeys
                    ? normalizedName(key, name + " key") : requireText(key, name + " key");
            List<String> validValues = List.copyOf(
                    Objects.requireNonNull(values, name + " values must not be null"));
            if (copy.putIfAbsent(validKey, validValues) != null) {
                throw new IllegalArgumentException("Duplicate " + name + " key: " + validKey);
            }
        });
        return Map.copyOf(copy);
    }

    private static Map<String, String> immutableStringMap(Map<String, String> source, String name) {
        Objects.requireNonNull(source, name + " must not be null");
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(
                requireText(key, name + " key"),
                Objects.requireNonNull(value, name + " value must not be null")));
        return Map.copyOf(copy);
    }

    private static String normalizedName(String value, String name) {
        return requireText(value, name).toLowerCase(Locale.ROOT);
    }

    private static String requirePath(String value) {
        String path = requireText(value, "path");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        return path;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

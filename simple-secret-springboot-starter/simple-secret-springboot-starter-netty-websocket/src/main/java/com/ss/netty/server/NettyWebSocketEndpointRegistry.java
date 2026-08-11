package com.ss.netty.server;

import com.ss.netty.auth.NettyWebSocketAuthenticator;
import com.ss.netty.config.NettyWebSocketProperties;
import com.ss.netty.handler.NettyWebSocketMessageHandler;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 已校验端点和入站消息处理器索引。 */
public final class NettyWebSocketEndpointRegistry {

    private final Map<String, NettyWebSocketEndpoint> endpoints;
    private final Map<String, NettyWebSocketMessageHandler> handlers;

    /** 校验服务配置并编译端点和处理器索引。 */
    public NettyWebSocketEndpointRegistry(NettyWebSocketProperties properties,
                                          List<NettyWebSocketMessageHandler> handlers,
                                          NettyWebSocketAuthenticator authenticator) {
        NettyWebSocketProperties required = Objects.requireNonNull(
                properties, "properties must not be null");
        validateServerProperties(required);
        this.endpoints = indexEndpoints(required, authenticator);
        this.handlers = indexHandlers(handlers, this.endpoints);
    }

    /** 返回指定路径的端点。 */
    public Optional<NettyWebSocketEndpoint> endpoint(String path) {
        return Optional.ofNullable(endpoints.get(requirePath(path)));
    }

    /** 返回指定路径的入站处理器；推送专用端点为空。 */
    public Optional<NettyWebSocketMessageHandler> handler(String path) {
        return Optional.ofNullable(handlers.get(requirePath(path)));
    }

    /** 返回全部已启用端点的只读索引。 */
    public Map<String, NettyWebSocketEndpoint> endpoints() {
        return endpoints;
    }

    /**
     * 校验握手 Origin。
     *
     * <p>没有 Origin 的非浏览器客户端允许连接；配置白名单时精确匹配，否则要求 Origin 与 Host 同源。</p>
     */
    public boolean isOriginAllowed(String path, String origin, String hostHeader) {
        NettyWebSocketEndpoint endpoint = endpoints.get(requirePath(path));
        if (endpoint == null) {
            return false;
        }
        if (origin == null || origin.isBlank()) {
            return true;
        }
        String requestedOrigin = origin.trim();
        if (!endpoint.allowedOrigins().isEmpty()) {
            return endpoint.allowedOrigins().contains(requestedOrigin);
        }
        return sameOrigin(requestedOrigin, hostHeader);
    }

    private static Map<String, NettyWebSocketEndpoint> indexEndpoints(
            NettyWebSocketProperties properties, NettyWebSocketAuthenticator authenticator) {
        Map<String, NettyWebSocketEndpoint> indexed = new LinkedHashMap<>();
        Map<String, NettyWebSocketProperties.Endpoint> configured = properties.getEndpoints();
        if (configured != null) {
            configured.forEach((name, value) -> {
                String validName = requireText(name, "endpoint name");
                NettyWebSocketProperties.Endpoint endpoint = Objects.requireNonNull(
                        value, "endpoint " + validName + " must not be null");
                if (!endpoint.isEnabled()) {
                    return;
                }
                String path = requirePath(endpoint.getPath());
                Set<String> allowedOrigins = validateOrigins(endpoint.getAllowedOrigins());
                NettyWebSocketEndpoint compiled = new NettyWebSocketEndpoint(
                        validName, path, endpoint.isAuthenticationRequired(), allowedOrigins);
                if (indexed.putIfAbsent(path, compiled) != null) {
                    throw new IllegalStateException("Duplicate WebSocket endpoint path: " + path);
                }
            });
        }
        if (indexed.isEmpty()) {
            throw new IllegalStateException("At least one enabled WebSocket endpoint is required");
        }
        if (authenticator == null && indexed.values().stream()
                .anyMatch(NettyWebSocketEndpoint::authenticationRequired)) {
            throw new IllegalStateException(
                    "Authenticated WebSocket endpoints require an authenticator bean");
        }
        return Map.copyOf(indexed);
    }

    private static Map<String, NettyWebSocketMessageHandler> indexHandlers(
            List<NettyWebSocketMessageHandler> source,
            Map<String, NettyWebSocketEndpoint> endpoints) {
        Objects.requireNonNull(source, "handlers must not be null");
        Map<String, NettyWebSocketMessageHandler> indexed = new LinkedHashMap<>();
        for (NettyWebSocketMessageHandler handler : source) {
            NettyWebSocketMessageHandler required = Objects.requireNonNull(
                    handler, "handler must not be null");
            String path = requirePath(required.path());
            if (!endpoints.containsKey(path)) {
                throw new IllegalStateException(
                        "WebSocket handler path has no configured endpoint: " + path);
            }
            if (indexed.putIfAbsent(path, required) != null) {
                throw new IllegalStateException("Duplicate WebSocket handler path: " + path);
            }
        }
        return Map.copyOf(indexed);
    }

    private static void validateServerProperties(NettyWebSocketProperties properties) {
        requireText(properties.getHost(), "host");
        requireRange(properties.getPort(), 0, 65_535, "port");
        requireRange(properties.getBossThreads(), 1, Integer.MAX_VALUE, "boss-threads");
        requireRange(properties.getWorkerThreads(), 0, Integer.MAX_VALUE, "worker-threads");
        requireRange(properties.getMaxHttpContentLength(), 1, Integer.MAX_VALUE,
                "max-http-content-length");
        requireRange(properties.getMaxFramePayloadLength(), 1, Integer.MAX_VALUE,
                "max-frame-payload-length");
        requirePositive(properties.getHandshakeTimeout(), "handshake-timeout");
        requirePositive(properties.getShutdownTimeout(), "shutdown-timeout");
        requireRange(properties.getHandlerCoreSize(), 1, Integer.MAX_VALUE,
                "handler-core-size");
        if (properties.getHandlerMaxSize() < properties.getHandlerCoreSize()) {
            throw new IllegalStateException(
                    "handler-max-size must be greater than or equal to handler-core-size");
        }
        requireRange(properties.getHandlerQueueCapacity(), 1, Integer.MAX_VALUE,
                "handler-queue-capacity");
        long totalHandlerCapacity = (long) properties.getHandlerMaxSize()
                + properties.getHandlerQueueCapacity();
        if (totalHandlerCapacity > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "handler capacity must not exceed " + Integer.MAX_VALUE);
        }
    }

    private static Set<String> validateOrigins(List<String> origins) {
        if (origins == null || origins.isEmpty()) {
            return Set.of();
        }
        Set<String> validated = new LinkedHashSet<>();
        for (String origin : origins) {
            String value = requireText(origin, "allowed-origin");
            if ("*".equals(value)) {
                throw new IllegalStateException("allowed-origin '*' is not supported");
            }
            URI uri = parseOrigin(value);
            if (uri.getUserInfo() != null || uri.getPath() != null && !uri.getPath().isEmpty()
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalStateException("allowed-origin must contain only scheme and authority");
            }
            validated.add(value);
        }
        return Set.copyOf(validated);
    }

    private static boolean sameOrigin(String origin, String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return false;
        }
        try {
            URI originUri = parseOrigin(origin);
            URI hostUri = new URI(originUri.getScheme() + "://" + hostHeader.trim());
            return originUri.getHost() != null && hostUri.getHost() != null
                    && originUri.getHost().equalsIgnoreCase(hostUri.getHost())
                    && effectivePort(originUri) == effectivePort(hostUri);
        } catch (IllegalStateException | URISyntaxException exception) {
            return false;
        }
    }

    private static URI parseOrigin(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalStateException("origin must be an absolute HTTP(S) origin");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("origin must be a valid URI", exception);
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " must be positive");
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalStateException(
                    name + " must be between " + minimum + " and " + maximum);
        }
    }

    private static String requirePath(String value) {
        String path = requireText(value, "endpoint path");
        if (!path.startsWith("/") || path.contains("?") || path.contains("#")) {
            throw new IllegalStateException(
                    "endpoint path must start with '/' and contain no query or fragment");
        }
        return path;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank");
        }
        return value.trim();
    }
}

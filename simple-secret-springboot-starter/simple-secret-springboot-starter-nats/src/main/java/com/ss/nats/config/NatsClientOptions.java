package com.ss.nats.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 单个 NATS 客户端的连接与操作配置。
 */
public class NatsClientOptions {
    private static final Set<String> SUPPORTED_SCHEMES = Set.of("nats", "tls", "ws", "wss");

    /**
     * 是否启用。
     */
    private boolean enabled;
    /**
     * 服务连接地址。
     */
    private String url;
    /**
     * NATS 连接名称。
     */
    private String connectionName;
    /**
     * 用户名。
     */
    private String username;
    /**
     * 密码。
     */
    private String password;
    /**
     * 是否启用自动重连。
     */
    private boolean reconnectEnabled = true;
    /**
     * 最大重连次数。
     */
    private int maxReconnects = -1;
    /**
     * 重连等待时间，单位毫秒。
     */
    private long reconnectWaitMillis = 5_000L;
    /**
     * 重连抖动上限，单位毫秒。
     */
    private long reconnectJitterMillis = 2_000L;
    /**
     * 连接调度超时时间，单位毫秒。
     */
    private long connectionTimeoutMillis = 10_000L;
    /**
     * 发布超时时间，单位毫秒。
     */
    private long publishTimeoutMillis = 10_000L;
    /**
     * 请求对象超时时间，单位毫秒。
     */
    private long requestTimeoutMillis = 30_000L;

    /** @return 是否启用客户端 */ public boolean isEnabled() { return enabled; }
    /** @param value 是否启用客户端 */ public void setEnabled(boolean value) { enabled = value; }
    /** @return NATS 服务器 URL */ public String getUrl() { return url; }
    /** @param value NATS 服务器 URL */ public void setUrl(String value) { url = value; }
    /** @return 连接名称 */ public String getConnectionName() { return connectionName; }
    /** @param value 连接名称 */ public void setConnectionName(String value) { connectionName = value; }
    /** @return 用户名 */ public String getUsername() { return username; }
    /** @param value 用户名 */ public void setUsername(String value) { username = value; }
    /** @return 密码 */ public String getPassword() { return password; }
    /** @param value 密码 */ public void setPassword(String value) { password = value; }
    /** @return 是否启用重连 */ public boolean isReconnectEnabled() { return reconnectEnabled; }
    /** @param value 是否启用重连 */ public void setReconnectEnabled(boolean value) { reconnectEnabled = value; }
    /** @return 最大重连次数，-1 表示无限 */ public int getMaxReconnects() { return maxReconnects; }
    /** @param value 最大重连次数 */ public void setMaxReconnects(int value) { maxReconnects = value; }
    /** @return 重连等待毫秒数 */ public long getReconnectWaitMillis() { return reconnectWaitMillis; }
    /** @param value 重连等待毫秒数 */ public void setReconnectWaitMillis(long value) { reconnectWaitMillis = value; }
    /** @return 重连抖动毫秒数 */ public long getReconnectJitterMillis() { return reconnectJitterMillis; }
    /** @param value 重连抖动毫秒数 */ public void setReconnectJitterMillis(long value) { reconnectJitterMillis = value; }
    /** @return 连接超时毫秒数 */ public long getConnectionTimeoutMillis() { return connectionTimeoutMillis; }
    /** @param value 连接超时毫秒数 */ public void setConnectionTimeoutMillis(long value) { connectionTimeoutMillis = value; }
    /** @return 发布 flush 超时毫秒数 */ public long getPublishTimeoutMillis() { return publishTimeoutMillis; }
    /** @param value 发布 flush 超时毫秒数 */ public void setPublishTimeoutMillis(long value) { publishTimeoutMillis = value; }
    /** @return 请求超时毫秒数 */ public long getRequestTimeoutMillis() { return requestTimeoutMillis; }
    /** @param value 请求超时毫秒数 */ public void setRequestTimeoutMillis(long value) { requestTimeoutMillis = value; }

    /**
     * 返回显式连接名，未配置时使用稳定的 clientKey 派生名称。
     *
     * @param clientKey 客户端键
     * @return 匹配结果；未找到时的行为见方法说明
     */
    public String resolveConnectionName(String clientKey) {
        if (connectionName != null && !connectionName.isBlank()) {
            return connectionName.trim();
        }
        return "simple-secret-nats-" + requireClientKey(clientKey);
    }

    /**
     * 校验启用客户端配置。
     *
     * @param clientKey 客户端键
     */
    public void validate(String clientKey) {
        requireClientKey(clientKey);
        if (!enabled) {
            return;
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("NATS client " + clientKey + " requires URL");
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !SUPPORTED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("NATS URL scheme must be nats, tls, ws or wss");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("NATS client " + clientKey + " has invalid URL", exception);
        }
        if (password != null && !password.isBlank()
                && (username == null || username.isBlank())) {
            throw new IllegalArgumentException("NATS username is required when password is configured");
        }
        if (maxReconnects < -1) {
            throw new IllegalArgumentException("max reconnects must be -1 or greater");
        }
        if (reconnectWaitMillis < 0 || reconnectJitterMillis < 0) {
            throw new IllegalArgumentException("reconnect wait and jitter must be non-negative");
        }
        if (connectionTimeoutMillis <= 0) {
            throw new IllegalArgumentException("connection timeout must be positive");
        }
        if (publishTimeoutMillis <= 0) {
            throw new IllegalArgumentException("publish timeout must be positive");
        }
        if (requestTimeoutMillis <= 0) {
            throw new IllegalArgumentException("request timeout must be positive");
        }
    }

    private static String requireClientKey(String clientKey) {
        if (clientKey == null || clientKey.isBlank() || containsWhitespace(clientKey)) {
            throw new IllegalArgumentException("NATS clientKey must be non-blank and contain no whitespace");
        }
        return clientKey.trim();
    }

    private static boolean containsWhitespace(String value) {
        return value.codePoints().anyMatch(Character::isWhitespace);
    }
}

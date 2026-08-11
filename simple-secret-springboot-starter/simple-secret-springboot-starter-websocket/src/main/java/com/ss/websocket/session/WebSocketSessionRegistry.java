package com.ss.websocket.session;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** 当前应用实例内的 WebSocket 会话注册表。 */
public final class WebSocketSessionRegistry {

    private final int sendTimeLimitMillis;
    private final int sendBufferSizeBytes;
    private final Map<String, Map<String, Map<String, WebSocketSession>>> sessions =
            new ConcurrentHashMap<>();

    /**
     * 创建会话注册表。
     *
     * @param sendTimeLimitMillis 单次发送最大耗时，单位毫秒
     * @param sendBufferSizeBytes 并发发送缓冲区上限，单位字节
     */
    public WebSocketSessionRegistry(int sendTimeLimitMillis, int sendBufferSizeBytes) {
        if (sendTimeLimitMillis <= 0) {
            throw new IllegalArgumentException("sendTimeLimitMillis must be positive");
        }
        if (sendBufferSizeBytes <= 0) {
            throw new IllegalArgumentException("sendBufferSizeBytes must be positive");
        }
        this.sendTimeLimitMillis = sendTimeLimitMillis;
        this.sendBufferSizeBytes = sendBufferSizeBytes;
    }

    /**
     * 注册一个连接。同一路径和会话键可以保留多个连接。
     *
     * @param path WebSocket 端点路径
     * @param sessionKey 会话分组键
     * @param session 原始会话
     * @return 用于并发安全发送的会话
     */
    public WebSocketSession register(String path, String sessionKey, WebSocketSession session) {
        String validPath = requirePath(path);
        String validKey = requireText(sessionKey, "sessionKey");
        WebSocketSession requiredSession = Objects.requireNonNull(session, "session must not be null");
        String sessionId = requireText(requiredSession.getId(), "session.id");
        WebSocketSession sendSession = requiredSession instanceof ConcurrentWebSocketSessionDecorator
                ? requiredSession
                : new ConcurrentWebSocketSessionDecorator(
                        requiredSession, sendTimeLimitMillis, sendBufferSizeBytes);
        sessions.computeIfAbsent(validPath, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(validKey, ignored -> new ConcurrentHashMap<>())
                .put(sessionId, sendSession);
        return sendSession;
    }

    /**
     * 按连接 ID 精确移除会话。
     *
     * @return {@code true} 表示确实移除了连接
     */
    public boolean remove(String path, String sessionKey, String sessionId) {
        String validPath = requirePath(path);
        String validKey = requireText(sessionKey, "sessionKey");
        String validSessionId = requireText(sessionId, "sessionId");
        AtomicBoolean removed = new AtomicBoolean();
        sessions.computeIfPresent(validPath, (ignoredPath, keyedSessions) -> {
            keyedSessions.computeIfPresent(validKey, (ignoredKey, identifiedSessions) -> {
                removed.set(identifiedSessions.remove(validSessionId) != null);
                return identifiedSessions.isEmpty() ? null : identifiedSessions;
            });
            return keyedSessions.isEmpty() ? null : keyedSessions;
        });
        return removed.get();
    }

    /** 获取指定路径和会话键的全部连接。 */
    public List<WebSocketSession> sessions(String path, String sessionKey) {
        Map<String, Map<String, WebSocketSession>> keyedSessions = sessions.get(requirePath(path));
        if (keyedSessions == null) {
            return List.of();
        }
        Map<String, WebSocketSession> identifiedSessions = keyedSessions.get(
                requireText(sessionKey, "sessionKey"));
        return identifiedSessions == null ? List.of() : List.copyOf(identifiedSessions.values());
    }

    /** 获取指定路径下的全部连接。 */
    public List<WebSocketSession> sessions(String path) {
        Map<String, Map<String, WebSocketSession>> keyedSessions = sessions.get(requirePath(path));
        if (keyedSessions == null) {
            return List.of();
        }
        return keyedSessions.values().stream()
                .flatMap(identifiedSessions -> identifiedSessions.values().stream())
                .toList();
    }

    /**
     * 获取各路径当前连接数量的只读快照。
     *
     * @return 路径到连接数量的映射
     */
    public Map<String, Integer> snapshot() {
        return Map.copyOf(sessions.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().values().stream()
                        .mapToInt(Map::size)
                        .sum())));
    }

    private static String requirePath(String path) {
        String validPath = requireText(path, "path");
        if (!validPath.startsWith("/")) {
            throw new IllegalArgumentException("path must start with '/'");
        }
        return validPath;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

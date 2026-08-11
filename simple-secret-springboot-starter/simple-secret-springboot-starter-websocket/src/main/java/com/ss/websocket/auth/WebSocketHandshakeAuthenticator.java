package com.ss.websocket.auth;

import com.ss.websocket.session.WebSocketPrincipal;
import org.springframework.http.server.ServerHttpRequest;

import java.util.Optional;

/** 应用侧 WebSocket 握手认证扩展点。 */
@FunctionalInterface
public interface WebSocketHandshakeAuthenticator {

    /**
     * 认证当前握手请求。
     *
     * @param request 握手请求
     * @return 认证身份；空值表示拒绝握手
     */
    Optional<WebSocketPrincipal> authenticate(ServerHttpRequest request);
}

package com.ss.netty.auth;

import java.util.Optional;

/** 应用侧 Netty WebSocket 握手认证扩展点。 */
@FunctionalInterface
public interface NettyWebSocketAuthenticator {

    /**
     * 认证当前握手请求。
     *
     * @return 认证身份；空值表示拒绝握手

     *
     * @param request 请求对象
     */
    Optional<NettyWebSocketPrincipal> authenticate(NettyWebSocketHandshakeRequest request);
}

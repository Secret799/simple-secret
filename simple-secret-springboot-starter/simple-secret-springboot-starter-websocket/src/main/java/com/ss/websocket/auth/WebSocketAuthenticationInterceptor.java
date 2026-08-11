package com.ss.websocket.auth;

import com.ss.websocket.session.WebSocketPrincipal;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Objects;

/** 将应用认证结果写入 WebSocket 会话属性的握手拦截器。 */
public final class WebSocketAuthenticationInterceptor implements HandshakeInterceptor {

    /** 会话属性中的认证身份键。 */
    public static final String PRINCIPAL_ATTRIBUTE =
            WebSocketAuthenticationInterceptor.class.getName() + ".principal";

    private final WebSocketHandshakeAuthenticator authenticator;

    /** 创建认证握手拦截器。 */
    public WebSocketAuthenticationInterceptor(WebSocketHandshakeAuthenticator authenticator) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
    }

    /** 调用应用认证器并保存成功结果。 */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            return authenticator.authenticate(request)
                    .map(principal -> storePrincipal(attributes, principal))
                    .orElse(false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** 握手完成后无需额外处理。 */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No resources are allocated during authentication.
    }

    private static boolean storePrincipal(Map<String, Object> attributes,
                                          WebSocketPrincipal principal) {
        attributes.put(PRINCIPAL_ATTRIBUTE, principal);
        return true;
    }
}

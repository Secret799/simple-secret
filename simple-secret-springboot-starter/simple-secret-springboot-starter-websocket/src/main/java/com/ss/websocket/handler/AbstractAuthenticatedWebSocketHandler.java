package com.ss.websocket.handler;

import com.ss.websocket.auth.WebSocketAuthenticationInterceptor;
import com.ss.websocket.session.WebSocketPrincipal;
import com.ss.websocket.session.WebSocketSessionRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Objects;

/** 自动维护认证身份会话生命周期的 WebSocket 处理器基类。 */
public abstract class AbstractAuthenticatedWebSocketHandler extends AbstractSimpleSecretWebSocketHandler {

    private final WebSocketSessionRegistry registry;

    /** 创建认证端点处理器。 */
    protected AbstractAuthenticatedWebSocketHandler(String path, WebSocketSessionRegistry registry) {
        super(path);
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /** 校验握手身份、注册连接并调用业务钩子。 */
    @Override
    public final void afterConnectionEstablished(WebSocketSession session) throws Exception {
        WebSocketPrincipal principal = principalOrNull(session);
        if (principal == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        registry.register(path(), principal.sessionKey(), session);
        try {
            onConnectionEstablished(session, principal);
        } catch (Exception | Error failure) {
            registry.remove(path(), principal.sessionKey(), session.getId());
            throw failure;
        }
    }

    /** 精确注销当前连接并调用业务钩子。 */
    @Override
    public final void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        WebSocketPrincipal principal = principalOrNull(session);
        if (principal != null) {
            registry.remove(path(), principal.sessionKey(), session.getId());
        }
        onConnectionClosed(session, status, principal);
    }

    /** 获取当前会话的认证身份。 */
    protected final WebSocketPrincipal principal(WebSocketSession session) {
        WebSocketPrincipal principal = principalOrNull(session);
        if (principal == null) {
            throw new IllegalStateException("WebSocket principal is unavailable");
        }
        return principal;
    }

    /** 认证连接建立后的业务钩子。 */
    protected void onConnectionEstablished(WebSocketSession session,
                                           WebSocketPrincipal principal) throws Exception {
        // Default implementation intentionally does nothing.
    }

    /** 认证连接关闭后的业务钩子。 */
    protected void onConnectionClosed(WebSocketSession session, CloseStatus status,
                                      WebSocketPrincipal principal) throws Exception {
        // Default implementation intentionally does nothing.
    }

    private static WebSocketPrincipal principalOrNull(WebSocketSession session) {
        Object value = session.getAttributes().get(
                WebSocketAuthenticationInterceptor.PRINCIPAL_ATTRIBUTE);
        return value instanceof WebSocketPrincipal principal ? principal : null;
    }
}

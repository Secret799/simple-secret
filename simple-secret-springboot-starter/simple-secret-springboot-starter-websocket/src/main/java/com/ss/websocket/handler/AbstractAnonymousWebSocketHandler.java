package com.ss.websocket.handler;

/** 不要求握手身份的 WebSocket 处理器基类。 */
public abstract class AbstractAnonymousWebSocketHandler extends AbstractSimpleSecretWebSocketHandler {

    /** 创建匿名端点处理器。 */
    protected AbstractAnonymousWebSocketHandler(String path) {
        super(path);
    }
}

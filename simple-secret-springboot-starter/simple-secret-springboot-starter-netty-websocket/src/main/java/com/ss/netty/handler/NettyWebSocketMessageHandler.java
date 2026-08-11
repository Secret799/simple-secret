package com.ss.netty.handler;

import com.ss.netty.message.NettyWebSocketMessage;

/** 应用侧 Netty WebSocket 文本消息处理器。 */
public interface NettyWebSocketMessageHandler {

    /** 返回该处理器绑定的已配置端点路径。 */
    String path();

    /** 处理一条完整文本消息。 */
    void handle(NettyWebSocketMessage message);
}

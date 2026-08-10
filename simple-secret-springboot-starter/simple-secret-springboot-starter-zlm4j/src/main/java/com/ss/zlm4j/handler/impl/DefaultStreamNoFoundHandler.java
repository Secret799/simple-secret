package com.ss.zlm4j.handler.impl;

import com.ss.zlm4j.domain.MediaInfoDomain;
import com.ss.zlm4j.domain.SocketInfoDomain;
import com.ss.zlm4j.handler.StreamNoFoundHandler;

/**
 * Zlm 流未找到作用域处理器（可用于未找到流后按需拉流）
 *
 * @author JunPzx
 * @since 2025/8/21 15:18
 */
public class DefaultStreamNoFoundHandler extends AbstractCallbackHandler implements StreamNoFoundHandler {
    @Override
    public int handle(MediaInfoDomain mediaInfoDomain, SocketInfoDomain socketInfoDomain) {
        // 如果没有找到流，可以根据业务去指定地方拉流
        return 1;
    }
}

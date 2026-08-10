package com.ss.zlm4j.handler;

import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_PUBLISH_AUTH_INVOKER;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;

/**
 * 推流回调 作用域处理器
 *
 * @author JunPzx
 * @since 2024/8/8 18:32
 */
public interface StreamPublishHandler {

    /**
     * 收到rtsp/rtmp推流事件广播，通过该事件控制推流鉴权
     *
     * @param urlInfo 推流url相关信息
     * @param invoker 执行invoker返回鉴权结果
     * @param sender  该tcp客户端相关信息
     */
    void handle(MK_MEDIA_INFO urlInfo, MK_PUBLISH_AUTH_INVOKER invoker, MK_SOCK_INFO sender);
}

package com.ss.zlm4j.handler;

import com.aizuda.zlm4j.structure.MK_AUTH_INVOKER;
import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;

/**
 * 播放回调 作用域处理器
 *
 * @author JunPzx
 * @since 2024/8/8 18:32
 */
public interface StreamPlayHandler {

    /**
     * 播放rtsp/rtmp/http-flv/hls事件广播，通过该事件控制播放鉴权
     *
     * @param urlInfo 播放url相关信息
     * @param invoker 执行invoker返回鉴权结果
     * @param sender  播放客户端相关信息
     */
    void handle(MK_MEDIA_INFO urlInfo, MK_AUTH_INVOKER invoker, MK_SOCK_INFO sender);
}

package com.ss.zlm4j.callback;

import java.util.Optional;

import com.aizuda.zlm4j.callback.IMKPublishCallBack;
import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_PUBLISH_AUTH_INVOKER;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.handler.StreamPublishHandler;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import lombok.extern.slf4j.Slf4j;

/**
 * 推流回调
 *
 * @author junpzx
 * @since 2023/11/29
 **/
@Slf4j
public class MKPublishCallBack implements IMKPublishCallBack {

    private final StreamPublishHandler handler;

    /**
     * 创建并初始化实例。
     *
     * @param handler 消息处理
     */
    public MKPublishCallBack(StreamPublishHandler handler) {
        //回调使用同一个线程
        Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "MediaPublishThread"));
        this.handler = handler;
    }

    /**
     * 收到rtsp/rtmp推流事件广播，通过该事件控制推流鉴权
     *
     * @param urlInfo 推流url相关信息
     * @param invoker 执行invoker返回鉴权结果
     * @param sender  该tcp客户端相关信息
     * @see " mk_publish_auth_invoker_do"
     */
    @Override
    public void invoke(MK_MEDIA_INFO urlInfo, MK_PUBLISH_AUTH_INVOKER invoker, MK_SOCK_INFO sender) {
        log.info("【SimpleSecretZLMediaKit】 rtsp/rtmp推流事件 回调开始");
        try {
            Optional.ofNullable(handler).ifPresent(t -> t.handle(urlInfo, invoker, sender));
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】 rtsp/rtmp推流事件 回调处理器发生异常", e);
        }
        log.info("【SimpleSecretZLMediaKit】 rtsp/rtmp推流事件 回调结束");
    }
}

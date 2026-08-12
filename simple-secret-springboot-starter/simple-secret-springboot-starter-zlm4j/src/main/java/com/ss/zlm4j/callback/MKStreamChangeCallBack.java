package com.ss.zlm4j.callback;

import java.util.Optional;

import com.aizuda.zlm4j.callback.IMKStreamChangeCallBack;
import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.ss.zlm4j.support.SpringUtils;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.event.StreamDeregisterEvent;
import com.ss.zlm4j.event.StreamRegisteredEvent;
import com.ss.zlm4j.handler.StreamChangeHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import lombok.extern.slf4j.Slf4j;

/**
 * 注册或反注册MediaSource事件广播
 *
 * @author junpzx
 * @since 2023/11/23
 **/
@Slf4j
public class MKStreamChangeCallBack implements IMKStreamChangeCallBack {

    private final StreamChangeHandler handler;

    /**
     * 创建并初始化实例。
     *
     * @param handler 消息处理
     */
    public MKStreamChangeCallBack(StreamChangeHandler handler) {
        Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "MediaStreamChangeThread"));
        this.handler = handler;
    }

    /**
     * 注册或反注册MediaSource事件广播
     *
     * @param regist 注册为1，注销为0
     * @param sender 该MediaSource对象
     */
    @Override
    public void invoke(int regist, MK_MEDIA_SOURCE sender) {
        log.info("【SimpleSecretZLMediaKit】 注册或反注册事件 回调开始");
        // 处理器处理
        try {
            Optional.ofNullable(handler).ifPresent(t -> t.handle(regist, sender));
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】 注册或反注册事件 回调处理器发生异常", e);
        }
        // 判断当前时注销还是注册
        MediaSourceDomain mediaSource = regist == 1 ?
                ZlmMediaHelper.Assembler.getMediaSource(sender, true) :
                ZlmMediaHelper.Assembler.getMediaSource(sender, false);
        // 发布事件
        if (regist == 1) {
            SpringUtils.publishEvent(new StreamRegisteredEvent(mediaSource));
        } else {
            SpringUtils.publishEvent(new StreamDeregisterEvent(mediaSource));
        }
        log.info("【SimpleSecretZLMediaKit】 注册或反注册事件 回调结束");
    }
}

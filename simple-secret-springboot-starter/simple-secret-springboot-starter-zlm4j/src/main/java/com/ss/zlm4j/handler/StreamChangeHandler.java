package com.ss.zlm4j.handler;

import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.helper.ZlmMediaHelper;

/**
 * 注册或反注册MediaSource事件处理器
 *
 * @author JunPzx
 * @since 2025/8/21 15:02
 */
public interface StreamChangeHandler {

    /**
     * 注册或反注册MediaSource事件广播
     *
     * @param register 注册为1，注销为0
     * @param sender   该MediaSource对象
     */
    default void handle(int register, MK_MEDIA_SOURCE sender) {
        //如果是register是注销情况下无法获取流详细信息如观看人数等
        if (register == 1) {
            handleRegister(sender);
        } else {
            handleDeregister(sender);
        }
    }

    /**
     * 处理注册
     *
     * @param sender 媒体源对象
     */
    default void handleRegister(MK_MEDIA_SOURCE sender) {
        handleRegister(ZlmMediaHelper.Assembler.getMediaSource(sender, true));
    }

    /**
     * 处理注册
     *
     * @param mediaSource 媒体源信息
     */
    default void handleRegister(MediaSourceDomain mediaSource) {
    }


    /**
     * 处理注销
     *
     * @param sender 媒体源对象
     */
    default void handleDeregister(MK_MEDIA_SOURCE sender) {
        handleDeregister(ZlmMediaHelper.Assembler.getMediaSource(sender, false));
    }

    /**
     * 处理注销
     *
     * @param mediaSource 媒体源信息
     */
    default void handleDeregister(MediaSourceDomain mediaSource) {
    }

}

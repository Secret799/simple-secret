package com.ss.zlm4j.callback;

import com.aizuda.zlm4j.callback.IMKSourceFindCallBack;
import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;

/**
 * 寻找流回调
 *
 * @author junpzx
 * @since 2024-06-12 13:49
 */
@Slf4j
public class MKSourceFindCallBack implements IMKSourceFindCallBack {
    private final IMKSourceHandleCallBack mkSourceHandleCallBack;

    /**
     * 创建并初始化实例。
     *
     * @param imkSourceHandleCallBack 媒体源句柄回调
     */
    public MKSourceFindCallBack(IMKSourceHandleCallBack imkSourceHandleCallBack) {
        Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "MediaSourceFindThread"));
        this.mkSourceHandleCallBack = imkSourceHandleCallBack;
    }

    @Override
    public void invoke(Pointer userData, MK_MEDIA_SOURCE ctx) {
        log.info("【SimpleSecretZLMediaKit】 寻找流事件 回调开始");
        mkSourceHandleCallBack.invoke(ctx);
        log.info("【SimpleSecretZLMediaKit】 寻找流事件 回调结束");
    }
}
package com.ss.zlm4j.callback;

import com.aizuda.zlm4j.callback.IMKNoFoundCallBack;
import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.support.SpringUtils;
import com.ss.zlm4j.event.StreamNoFoundEvent;
import com.ss.zlm4j.handler.StreamNoFoundHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import lombok.extern.slf4j.Slf4j;

/**
 * 未找到流后会广播该事件，请在监听该事件后去拉流或其他方式产生流，这样就能按需拉流了
 *
 * @author junpzx
 * @since 2023/11/23
 **/
@Slf4j
public class MKStreamNoFoundCallBack implements IMKNoFoundCallBack {

    private final StreamNoFoundHandler handler;


    public MKStreamNoFoundCallBack(StreamNoFoundHandler handler) {
        //回调使用同一个线程
        Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "MediaNoFoundThread"));
        this.handler = handler;
    }

    /**
     * 未找到流后会广播该事件，请在监听该事件后去拉流或其他方式产生流，这样就能按需拉流了
     *
     * @param urlInfo 播放url相关信息
     * @param sender  播放客户端相关信息
     * @return 1 直接关闭
     * 0 等待流注册
     */
    @Override
    public int invoke(MK_MEDIA_INFO urlInfo, MK_SOCK_INFO sender) {
        log.info("【SimpleSecretZLMediaKit】 流未找到事件 回调开始");
        try {
            return handler != null ? handler.handle(urlInfo, sender) : 1;
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】 流未找到事件 回调处理器发生异常", e);
        }
        // 发布事件
        SpringUtils.publishEvent(new StreamNoFoundEvent(ZlmMediaHelper.Assembler.getMediaInfo(urlInfo),
                ZlmMediaHelper.Assembler.getSocketInfo(sender)));
        log.info("【SimpleSecretZLMediaKit】 流未找到事件 回调结束");
        //这里可以实现按需拉流，这里面新起个线程去操作拉起流
        return 0;
    }
}

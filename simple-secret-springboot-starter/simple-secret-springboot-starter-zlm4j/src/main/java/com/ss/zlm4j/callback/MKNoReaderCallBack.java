package com.ss.zlm4j.callback;

import java.util.Optional;

import com.aizuda.zlm4j.callback.IMKNoReaderCallBack;
import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.event.StreamNoReaderEvent;
import com.ss.zlm4j.handler.StreamNoReaderHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.ss.zlm4j.support.SpringUtils;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import lombok.extern.slf4j.Slf4j;

/**
 * 无人观看回调
 *
 * @author junpzx
 * @since 2023/11/23
 **/
@Slf4j
public class MKNoReaderCallBack implements IMKNoReaderCallBack {

    private final StreamNoReaderHandler handler;

    /**
     * 创建并初始化实例。
     *
     * @param handler 消息处理
     */
    public MKNoReaderCallBack(StreamNoReaderHandler handler) {
        //回调使用同一个线程
        Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "MediaNoReaderThread"));
        this.handler = handler;
    }

    /**
     * 某个流无人消费时触发，目的为了实现无人观看时主动断开拉流等业务逻辑
     *
     * @param sender 该MediaSource对象
     */
    @Override
    public void invoke(MK_MEDIA_SOURCE sender) {
        MediaSourceDomain mediaSource = ZlmMediaHelper.Assembler.getMediaSource(sender);
        log.info("【SimpleSecretZLMediaKit】 流无人观看事件 回调开始,schema:{},app:{},stream:{}",
                mediaSource.getSchema(), mediaSource.getApp(), mediaSource.getStream());
        // 根据APP获取对应作用域的回调执行处理器
        try {
            Optional.ofNullable(handler).ifPresent(t -> t.handle(sender));
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】 流无人观看事件 回调处理器发生异常", e);
        }
        // 无人观看时候可以调用下面的实现关流 不调用就代表不关流 需要配置protocol.auto_close 为 0 这里才会有回调
        //         ZlmMediaHelper.getZlmApi().mk_media_source_close(sender, 1);
        // 发布无人观看事件
        SpringUtils.publishEvent(new StreamNoReaderEvent(mediaSource));
        log.info("【SimpleSecretZLMediaKit】 流无人观看事件 回调结束");
    }
}

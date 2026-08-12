package com.ss.zlm4j.callback;

import java.util.Optional;

import com.aizuda.zlm4j.callback.IMKRecordMp4CallBack;
import com.aizuda.zlm4j.structure.MK_RECORD_INFO;
import com.ss.zlm4j.support.SpringUtils;
import com.ss.zlm4j.event.RecordShardingFileEvent;
import com.ss.zlm4j.handler.RecordMp4Handler;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import lombok.extern.slf4j.Slf4j;

/**
 * 录制mp4分片文件成功后广播
 *
 * @author junpzx
 * @since 2024-06-12 13:49
 */
@Slf4j
public class MKRecordMp4CallBack implements IMKRecordMp4CallBack {

    private final RecordMp4Handler handler;

    /**
     * 创建并初始化实例。
     *
     * @param handler 消息处理
     */
    public MKRecordMp4CallBack(RecordMp4Handler handler) {
        //回调使用同一个线程
        Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "RecordMp4Thread"));
        this.handler = handler;
    }

    /**
     * 录制mp4分片文件成功后广播
     */
    @Override
    public void invoke(MK_RECORD_INFO info) {
        log.info("【SimpleSecretZLMediaKit】 录制MP4分片文件成功 回调开始");
        try {
            Optional.ofNullable(handler).ifPresent(t -> t.handle(info));
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】 录制MP4分片文件成功 回调处理器发生异常", e);
        }
        // 发布事件
        SpringUtils.publishEvent(new RecordShardingFileEvent(RecordShardingFileEvent.RecordSource.MP4, ZlmMediaHelper.Assembler.getRecordInfo(info)));
        log.info("【SimpleSecretZLMediaKit】 录制MP4分片文件成功 回调结束");
    }
}

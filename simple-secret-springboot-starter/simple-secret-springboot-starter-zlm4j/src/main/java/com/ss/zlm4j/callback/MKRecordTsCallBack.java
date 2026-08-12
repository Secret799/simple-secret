package com.ss.zlm4j.callback;

import java.util.Optional;

import com.aizuda.zlm4j.callback.IMKRecordTsCallBack;
import com.aizuda.zlm4j.structure.MK_RECORD_INFO;
import com.ss.zlm4j.support.SpringUtils;
import com.ss.zlm4j.event.RecordShardingFileEvent;
import com.ss.zlm4j.handler.RecordTsHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import lombok.extern.slf4j.Slf4j;

/**
 * 录制ts分片文件成功后广播
 *
 * @author junpzx
 * @since 2024-06-12 13:49
 */
@Slf4j
public class MKRecordTsCallBack implements IMKRecordTsCallBack {

    private final RecordTsHandler handler;

    /**
     * 创建并初始化实例。
     *
     * @param handler 消息处理
     */
    public MKRecordTsCallBack(RecordTsHandler handler) {
        //回调使用同一个线程
        Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "RecordTsThread"));
        this.handler = handler;
    }

    /**
     * 录制ts分片文件成功后广播
     */
    @Override
    public void invoke(MK_RECORD_INFO info) {
        log.info("【SimpleSecretZLMediaKit】 录制ts分片文件成功 回调开始");
        try {
            Optional.ofNullable(handler).ifPresent(t -> t.handle(info));
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】 录制ts分片文件成功 回调处理器发生异常", e);
        }
        // 发布事件
        SpringUtils.publishEvent(new RecordShardingFileEvent(RecordShardingFileEvent.RecordSource.TS, ZlmMediaHelper.Assembler.getRecordInfo(info)));
        log.info("【SimpleSecretZLMediaKit】 录制ts分片文件成功 回调结束");
    }
}

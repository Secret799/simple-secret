package com.ss.zlm4j.handler;

import com.aizuda.zlm4j.structure.MK_RECORD_INFO;
import com.ss.zlm4j.domain.RecordInfoDomain;
import com.ss.zlm4j.helper.ZlmMediaHelper;

/**
 * 录制mp4分片文件成功后广播
 *
 * @author JunPzx
 * @since 2025/8/21 14:53
 */
public interface RecordMp4Handler {

    /**
     * 录制mp4分片文件成功后广播
     *
     * @param info 录制信息
     */
    void handle(RecordInfoDomain info);


    /**
     * 录制mp4分片文件成功后广播
     *
     * @param info 录制信息
     */
    default void handle(MK_RECORD_INFO info) {
        handle(ZlmMediaHelper.Assembler.getRecordInfo(info));
    }
}

package com.ss.zlm4j.handler.impl;

import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.handler.StreamNoReaderHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 默认 无人观看作用域处理器
 *
 * @author JunPzx
 * @since 2025/8/21 15:20
 */
@Slf4j
public class DefaultStreamNoReaderHandler extends AbstractCallbackHandler implements StreamNoReaderHandler {

    @Override
    public void handle(MK_MEDIA_SOURCE sender) {
        // 如果无人观看，那么直接关闭
        MediaSourceDomain mediaSource = ZlmMediaHelper.Assembler.getMediaSource(sender);
        ZlmMediaHelper.getZlmApi().mk_media_source_close(sender, 1);
        log.info("【SimpleSecretZLMediaKit】关闭无人观看流成功,app:{},stream:{},scheme:{},观看总人数:{},创建时间:{},存活时间:{}秒",
                mediaSource.getApp(), mediaSource.getStream(), mediaSource.getSchema(), mediaSource.getTotalReaderCount(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(mediaSource.getCreateStamp() * 1000), ZoneId.systemDefault()),
                mediaSource.getAliveSecond());
    }
}

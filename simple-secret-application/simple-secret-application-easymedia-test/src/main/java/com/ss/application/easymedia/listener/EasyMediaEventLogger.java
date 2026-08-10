package com.ss.application.easymedia.listener;

import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.event.RecordShardingFileEvent;
import com.ss.zlm4j.event.StreamRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 记录测试应用中关键的 ZLM/EasyMedia 运行事件。
 */
@Component
public class EasyMediaEventLogger {

    private static final Logger LOG = LoggerFactory.getLogger(EasyMediaEventLogger.class);

    /**
     * 记录已注册媒体流，便于确认测试流是否已被 ZLM 识别。
     *
     * @param event 流注册事件
     */
    @EventListener
    public void onStreamRegistered(StreamRegisteredEvent event) {
        MediaSourceDomain mediaSource = event.getMediaSource();
        LOG.info("EasyMedia stream registered: app={}, stream={}, schema={}",
                mediaSource.getApp(), mediaSource.getStream(), mediaSource.getSchema());
    }

    /**
     * 记录生成的录像分片及其来源。
     *
     * @param event 录像分片事件
     */
    @EventListener
    public void onRecordSharding(RecordShardingFileEvent event) {
        LOG.info("EasyMedia record file created: source={}, record={}",
                event.getRecordSource(), event.getRecordInfoDomain());
    }
}

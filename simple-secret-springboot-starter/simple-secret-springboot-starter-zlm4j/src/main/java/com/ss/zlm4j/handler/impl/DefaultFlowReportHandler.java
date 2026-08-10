package com.ss.zlm4j.handler.impl;

import com.ss.zlm4j.domain.MediaInfoDomain;
import com.ss.zlm4j.domain.SocketInfoDomain;
import com.ss.zlm4j.handler.FlowReportHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认 停止rtsp/rtmp/http-flv会话后流量汇报事件处理器
 *
 * @author JunPzx
 * @since 2025/8/21 14:32
 */
@Slf4j
public class DefaultFlowReportHandler extends AbstractCallbackHandler implements FlowReportHandler {
    @Override
    public void handle(MediaInfoDomain mediaInfo, long totalBytes, long totalSeconds, int isPlayer, SocketInfoDomain socketInfo) {
        log.info("【SimpleSecretZLMediaKit】app:{},stream:{},scheme:{} 播放已停止，当前播放客户端为:{},消耗流量：{}字节，观看总时长：{}",
                mediaInfo.getApp(), mediaInfo.getSchema(), mediaInfo.getSchema(), isPlayer == 0 ? "非播放器" : "播放器", totalBytes, totalSeconds);
    }
}

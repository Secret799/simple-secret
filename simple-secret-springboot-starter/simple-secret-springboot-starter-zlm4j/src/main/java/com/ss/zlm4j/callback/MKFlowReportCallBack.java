package com.ss.zlm4j.callback;

import java.util.Optional;

import com.aizuda.zlm4j.callback.IMKFlowReportCallBack;
import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.support.SpringUtils;
import com.ss.zlm4j.domain.MediaInfoDomain;
import com.ss.zlm4j.domain.SocketInfoDomain;
import com.ss.zlm4j.event.FlowReportEvent;
import com.ss.zlm4j.handler.FlowReportHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 停止rtsp/rtmp/http-flv会话后流量汇报事件广播
 *
 * @author junpzx
 * @since 2024-06-12 13:49
 */
@Slf4j
@RequiredArgsConstructor
public class MKFlowReportCallBack implements IMKFlowReportCallBack {

    private final FlowReportHandler flowReportHandler;

    @Override
    public void invoke(MK_MEDIA_INFO urlInfo, long totalBytes, long totalSeconds, int isPlayer, MK_SOCK_INFO sender) {
        log.info("【SimpleSecretZLMediaKit】流量汇报事件 回调开始");
        try {
            Optional.ofNullable(flowReportHandler).ifPresent(t -> t.handle(urlInfo, totalBytes, totalSeconds, isPlayer, sender));
        } catch (Exception e) {
            log.error("【SimpleSecretZLMediaKit】流量汇报事件 回调处理器发生异常", e);
        }
        // 发布事件
        MediaInfoDomain mediaInfo = ZlmMediaHelper.Assembler.getMediaInfo(urlInfo);
        SocketInfoDomain socketInfo = ZlmMediaHelper.Assembler.getSocketInfo(sender);
        SpringUtils.publishEvent(new FlowReportEvent(mediaInfo, socketInfo, totalBytes, totalSeconds, isPlayer == 1));
        log.info("【SimpleSecretZLMediaKit】流量汇报事件 回调结束");
    }
}
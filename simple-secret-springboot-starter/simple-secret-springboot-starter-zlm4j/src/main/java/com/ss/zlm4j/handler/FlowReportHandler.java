package com.ss.zlm4j.handler;

import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.domain.MediaInfoDomain;
import com.ss.zlm4j.domain.SocketInfoDomain;
import com.ss.zlm4j.helper.ZlmMediaHelper;

/**
 * 停止rtsp/rtmp/http-flv会话后流量汇报事件处理器
 *
 * @author JunPzx
 * @since 2025/8/21 12:03
 */
public interface FlowReportHandler {

    /**
     * 停止rtsp/rtmp/http-flv会话后流量汇报事件广播
     *
     * @param urlInfo      播放url相关信息
     * @param totalBytes   耗费上下行总流量，单位字节数
     * @param totalSeconds 本次tcp会话时长，单位秒
     * @param isPlayer     客户端是否为播放器
     * @param sender       连接客户端信息
     */
    default void handle(MK_MEDIA_INFO urlInfo, long totalBytes, long totalSeconds, int isPlayer, MK_SOCK_INFO sender) {
        handle(ZlmMediaHelper.Assembler.getMediaInfo(urlInfo), totalBytes, totalSeconds, isPlayer, ZlmMediaHelper.Assembler.getSocketInfo(sender));
    }

    /**
     * 停止rtsp/rtmp/http-flv会话后流量汇报事件广播
     *
     * @param mediaInfo    播放url相关媒体信息
     * @param totalBytes   耗费上下行总流量，单位字节数
     * @param totalSeconds 本次tcp会话时长，单位秒
     * @param isPlayer     客户端是否为播放器
     * @param socketInfo   连接客户端信息
     */
    void handle(MediaInfoDomain mediaInfo, long totalBytes, long totalSeconds, int isPlayer, SocketInfoDomain socketInfo);
}

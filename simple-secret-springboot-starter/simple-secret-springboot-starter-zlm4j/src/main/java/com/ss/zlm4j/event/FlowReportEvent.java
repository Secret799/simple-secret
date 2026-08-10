package com.ss.zlm4j.event;

import com.ss.zlm4j.domain.MediaInfoDomain;
import com.ss.zlm4j.domain.SocketInfoDomain;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 停止rtsp/rtmp/http-flv会话后流量汇报事件
 *
 * @author JunPzx
 * @since 2025/8/20 12:00
 */
@Getter
public class FlowReportEvent extends ApplicationEvent {
    /**
     * 媒体相关信息
     */
    private final MediaInfoDomain mediaInfo;
    /**
     * 连接客户端信息
     */
    private final SocketInfoDomain socketInfo;
    /**
     * 耗费上下行总流量，单位字节数
     */
    private final long totalBytes;
    /**
     * 本次tcp会话时长，单位秒
     */
    private final long totalSeconds;
    /**
     * 客户端是否为播放器
     */
    private final boolean isPlayer;


    public FlowReportEvent(MediaInfoDomain mediaInfo, SocketInfoDomain socketInfo, long totalBytes, long totalSeconds, boolean isPlayer) {
        super(mediaInfo);
        this.isPlayer = isPlayer;
        this.mediaInfo = mediaInfo;
        this.socketInfo = socketInfo;
        this.totalBytes = totalBytes;
        this.totalSeconds = totalSeconds;
    }
}

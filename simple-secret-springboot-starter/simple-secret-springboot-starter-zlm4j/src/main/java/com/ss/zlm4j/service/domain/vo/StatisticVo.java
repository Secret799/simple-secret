package com.ss.zlm4j.service.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 内存占用信息
 *
 * @author lidaofu
 * @since 2024/5/20
 **/
@Data
public class StatisticVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1;

    /**
     * ZLMediaKit 媒体源对象数量。
     */
    private Long mediaSource;
    /**
     * 多协议媒体复用器对象数量。
     */
    private Long multiMediaSourceMuxer;
    /**
     * TCP 服务端对象数量。
     */
    private Long tcpServer;
    /**
     * TCP 会话对象数量。
     */
    private Long tcpSession;
    /**
     * UDP 服务端对象数量。
     */
    private Long udpServer;
    /**
     * UDP 会话对象数量。
     */
    private Long udpSession;
    /**
     * TCP 客户端对象数量。
     */
    private Long tcpClient;
    /**
     * ZLMediaKit 套接字对象数量。
     */
    private Long socket;
    /**
     * ZLMediaKit 帧实现对象数量。
     */
    private Long frameImp;
    /**
     * 视频帧尺寸。
     */
    private Long frame;
    /**
     * ZLMediaKit 缓冲区对象数量。
     */
    private Long buffer;
    /**
     * 原始缓冲区对象数量。
     */
    private Long bufferRaw;
    /**
     * 字符串型缓冲区对象数量。
     */
    private Long bufferLikeString;
    /**
     * 缓冲区列表对象数量。
     */
    private Long bufferList;
    /**
     * ZLMediaKit RTP 包对象数量。
     */
    private Long rtpPacket;
    /**
     * ZLMediaKit RTMP 包对象数量。
     */
    private Long rtmpPacket;
}

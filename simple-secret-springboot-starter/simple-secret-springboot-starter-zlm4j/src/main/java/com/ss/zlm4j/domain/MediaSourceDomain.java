package com.ss.zlm4j.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 流信息
 *
 * @author lidaofu
 * @since 2023/3/30
 **/
@Data
public class MediaSourceDomain implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * app
     */
    private String app;

    /**
     * 流id
     */
    private String stream;

    /**
     * schema
     */
    private String schema;

    //====================================以下信息只有在流媒体通道还存活时才有值============================================

    /**
     * 本协议观看人数
     */
    private Integer readerCount;

    /**
     * 产生源类型，包括 unknown = 0,rtmp_push=1,rtsp_push=2,rtp_push=3,pull=4,ffmpeg_pull=5,mp4_vod=6,device_chn=7
     */
    private Integer originType;

    /**
     * 产生源的url
     */
    private String originUrl;

    /**
     * 产生源的url的类型
     */
    private String originTypeStr;

    /**
     * 观看总数 包括hls/rtsp/rtmp/http-flv/ws-flv
     */
    private Integer totalReaderCount;


    /**
     * 存活时间，单位秒
     */
    private Integer aliveSecond;

    /**
     * 数据产生速度，单位byte/s
     */
    private Long bytesSpeed;

    /**
     * GMT unix系统时间戳，单位秒
     */
    private Long createStamp;

    /**
     * 是否录制Hls
     */
    private Boolean isRecordingHls;

    /**
     * 是否录制mp4
     */
    private Boolean isRecordingMp4;

    /**
     * 虚拟地址
     */
    private String vhost;

    /**
     * 轨道信息
     */
    private List<TrackDomain> tracks;
}

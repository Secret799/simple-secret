package com.ss.zlm4j.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 追踪信息
 *
 * @author junpz
 */
@Data
public class TrackDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 1;
    /**
     * 轨道是否为视频轨道。
     */
    private Integer isVideo;
    /**
     * {@code codec}标识。
     */
    private Integer codecId;
    /**
     * 编码器名称。
     */
    private String codecIdName;
    /**
     * 编码类型。
     */
    private Integer codecType;
    /**
     * 视频帧率。
     */
    private Integer fps;
    /**
     * 累计帧数。
     */
    private Integer frames;
    /**
     * 码流比特率。
     */
    private Integer bitRate;
    /**
     * 关键帧间隔，单位毫秒。
     */
    private Integer gopIntervalMs;
    /**
     * GOP 帧数。
     */
    private Integer gopSize;
    /**
     * 高度。
     */
    private Integer height;
    /**
     * 累计关键帧数。
     */
    private Integer keyFrames;
    /**
     * 媒体丢包率。
     */
    private Integer loss;
    /**
     * 是否已经就绪。
     */
    private Boolean ready;
    /**
     * 宽度。
     */
    private Integer width;
    /**
     * 音频采样率。
     */
    private Integer sampleRate;
    /**
     * 音频声道数。
     */
    private Integer audioChannel;
    /**
     * 音频采样位数。
     */
    private Integer audioSampleBit;

}
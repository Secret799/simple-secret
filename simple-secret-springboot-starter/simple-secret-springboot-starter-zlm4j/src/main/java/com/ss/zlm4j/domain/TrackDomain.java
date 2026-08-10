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
    private Integer isVideo;
    private Integer codecId;
    private String codecIdName;
    private Integer codecType;
    private Integer fps;
    private Integer frames;
    private Integer bitRate;
    private Integer gopIntervalMs;
    private Integer gopSize;
    private Integer height;
    private Integer keyFrames;
    private Integer loss;
    private Boolean ready;
    private Integer width;
    private Integer sampleRate;
    private Integer audioChannel;
    private Integer audioSampleBit;

}
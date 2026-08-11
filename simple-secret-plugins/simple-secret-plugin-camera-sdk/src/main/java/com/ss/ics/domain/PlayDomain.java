package com.ss.ics.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 实时预览或历史回放参数。 */
public class PlayDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private PlaybackParam playbackParam;
    private TakeStreamParam takeStreamParam;
    private VideoParam videoParam;

    /** @return 历史回放参数 */
    public PlaybackParam getPlaybackParam() {
        return playbackParam;
    }

    /** @param playbackParam 历史回放参数 @return 当前对象 */
    public PlayDomain setPlaybackParam(PlaybackParam playbackParam) {
        this.playbackParam = playbackParam;
        return this;
    }

    /** @return 取流参数 */
    public TakeStreamParam getTakeStreamParam() {
        return takeStreamParam;
    }

    /** @param takeStreamParam 取流参数 @return 当前对象 */
    public PlayDomain setTakeStreamParam(TakeStreamParam takeStreamParam) {
        this.takeStreamParam = takeStreamParam;
        return this;
    }

    /** @return 视频参数 */
    public VideoParam getVideoParam() {
        return videoParam;
    }

    /** @param videoParam 视频参数 @return 当前对象 */
    public PlayDomain setVideoParam(VideoParam videoParam) {
        this.videoParam = videoParam;
        return this;
    }

    /** 历史回放范围和倍率。 */
    public static class PlaybackParam implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String code;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private Double multiplier;

        /** @return 回放请求唯一编码 */
        public String getCode() {
            return code;
        }

        /** @param code 回放请求唯一编码 @return 当前对象 */
        public PlaybackParam setCode(String code) {
            this.code = code;
            return this;
        }

        /** @return 开始时间 */
        public LocalDateTime getBeginTime() {
            return beginTime;
        }

        /** @param beginTime 开始时间 @return 当前对象 */
        public PlaybackParam setBeginTime(LocalDateTime beginTime) {
            this.beginTime = beginTime;
            return this;
        }

        /** @return 结束时间 */
        public LocalDateTime getEndTime() {
            return endTime;
        }

        /** @param endTime 结束时间 @return 当前对象 */
        public PlaybackParam setEndTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }

        /** @return 播放倍率 */
        public Double getMultiplier() {
            return multiplier;
        }

        /** @param multiplier 播放倍率 @return 当前对象 */
        public PlaybackParam setMultiplier(Double multiplier) {
            this.multiplier = multiplier;
            return this;
        }
    }

    /** 视频尺寸、帧率和码率参数。 */
    public static class VideoParam implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Integer resolutionWidth;
        private Integer resolutionHeight;
        private Integer frameRate;
        private Integer bitRate;

        /** @return 分辨率宽度 */
        public Integer getResolutionWidth() {
            return resolutionWidth;
        }

        /** @param resolutionWidth 分辨率宽度 @return 当前对象 */
        public VideoParam setResolutionWidth(Integer resolutionWidth) {
            this.resolutionWidth = resolutionWidth;
            return this;
        }

        /** @return 分辨率高度 */
        public Integer getResolutionHeight() {
            return resolutionHeight;
        }

        /** @param resolutionHeight 分辨率高度 @return 当前对象 */
        public VideoParam setResolutionHeight(Integer resolutionHeight) {
            this.resolutionHeight = resolutionHeight;
            return this;
        }

        /** @return 帧率，未设置时返回 25 */
        public Integer getFrameRate() {
            return frameRate == null ? 25 : frameRate;
        }

        /** @param frameRate 帧率 @return 当前对象 */
        public VideoParam setFrameRate(Integer frameRate) {
            this.frameRate = frameRate;
            return this;
        }

        /** @return 码率 */
        public Integer getBitRate() {
            return bitRate;
        }

        /** @param bitRate 码率 @return 当前对象 */
        public VideoParam setBitRate(Integer bitRate) {
            this.bitRate = bitRate;
            return this;
        }
    }

    /** 厂商取流协议和编解码参数。 */
    public static class TakeStreamParam implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Integer streamType = 0;
        private String byProtoType = "0";
        private Integer videoEncode;
        private Integer audioEncode;

        /** @return 码流类型 */
        public Integer getStreamType() {
            return streamType;
        }

        /** @param streamType 码流类型 @return 当前对象 */
        public TakeStreamParam setStreamType(Integer streamType) {
            this.streamType = streamType;
            return this;
        }

        /** @return 厂商协议类型 */
        public String getByProtoType() {
            return byProtoType;
        }

        /** @param byProtoType 厂商协议类型 @return 当前对象 */
        public TakeStreamParam setByProtoType(String byProtoType) {
            this.byProtoType = byProtoType;
            return this;
        }

        /** @return 视频编码 */
        public Integer getVideoEncode() {
            return videoEncode;
        }

        /** @param videoEncode 视频编码 @return 当前对象 */
        public TakeStreamParam setVideoEncode(Integer videoEncode) {
            this.videoEncode = videoEncode;
            return this;
        }

        /** @return 音频编码 */
        public Integer getAudioEncode() {
            return audioEncode;
        }

        /** @param audioEncode 音频编码 @return 当前对象 */
        public TakeStreamParam setAudioEncode(Integer audioEncode) {
            this.audioEncode = audioEncode;
            return this;
        }
    }
}

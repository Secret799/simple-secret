package com.ss.zlm4j.service.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 拉流代理参数
 *
 * @author junpzx
 * @since 2024/06/12 15:36
 **/
@Data
@Accessors(chain = true)
public class StreamProxyPullerBO {

    /**
     * app
     */
    @NotBlank(message = "app不为空")
    private String app;

    /**
     * 流id
     */
    @NotBlank(message = "流id不为空")
    private String stream;

    /**
     * 代理流地址
     */
    @NotBlank(message = "代理流地址不为空")
    private String url;

    /**
     * 拉流重试次数(不传此参数或传值<=0时，则无限重试)
     */
    @NotNull(message = "拉流重试次数不能为空")
    private Integer retryCount;

    /**
     * 模式
     */
    private String schema;

    /**
     * 自动关闭
     */
    private Integer autoClose;

    /**
     * 拉流方式(rtsp拉流时，拉流方式，0：tcp，1：udp，2：组播)
     */
    private Integer rtpType = 0;

    /**
     * 拉流超时时间(单位秒，float类型)
     */
    private Integer timeoutSec;

    /**
     * 开启hls转码
     */
    private Integer enableHls;

    /**
     * 开启rtsp/webrtc转码
     */
    private Integer enableRtsp;

    /**
     * 开启rtmp/flv转码
     */
    private Integer enableRtmp;

    /**
     * 开启ts/ws转码
     */
    private Integer enableTs;

    /**
     * 转协议是否开启音频
     */
    private Integer enableAudio;

    /**
     * 开启转fmp4
     */
    private Integer enableFmp4;

    /**
     * 开启mp4录制
     */
    private Integer enableMp4;

    /**
     * mp4录制切片大小
     */
    private Integer mp4MaxSecond;

    /**
     * MP4点播(rtsp/rtmp/http-flv/ws-flv)是否循环播放文件
     */
    private Integer recordFileRepeat;

    /**
     * rtsp倍速
     */
    private BigDecimal rtspSpeed;
}

package com.ss.zlm4j.config.properties;

import com.ss.zlm4j.enums.SchemeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

import static com.ss.zlm4j.enums.SchemeEnum.*;

/**
 * 媒体服务器配置
 *
 * @author junpzx
 * @since 2023/11/29
 **/
@Data
@ConfigurationProperties(prefix = "simple-secret.zlm4j")
public class ZlmMediaProperties {
    /**
     * 是否启用
     */
    private Boolean enabled = false;
    /**
     * 线程数
     */
    private Integer threadNum = 5;
    /**
     * rtmp端口
     */
    private Integer rtmpPort = 7935;
    /**
     * rtsp端口
     */
    private Integer rtspPort = 7554;
    /**
     * http端口
     */
    private Integer httpPort = 7080;
    /**
     * rtc端口
     */
    private Integer rtcPort = 8000;
    /**
     * 是否启动原生 HTTP 监听器。
     */
    private Boolean httpListenerEnabled = true;
    /**
     * 是否启动原生 RTSP 监听器。
     */
    private Boolean rtspListenerEnabled = true;
    /**
     * 是否启动原生 RTMP 监听器。
     */
    private Boolean rtmpListenerEnabled = true;
    /**
     * 是否启动原生 RTC 监听器。
     */
    private Boolean rtcListenerEnabled = true;
    /**
     * 无人观看时，是否直接关闭(而不是通过on_none_reader hook返回close)
     * 此配置置1时，此流如果无人观看，将不触发on_none_reader hook回调，
     * 而是将直接关闭流
     */
    private Integer autoClose = 0;
    /**
     * 无人观看时间
     */
    private Integer streamNoneReaderDelayMs = 30000;
    /**
     * 最大等待时间
     */
    private Integer maxStreamWaitMs = 1500;
    /**
     * 截图连接和读取超时时间，单位毫秒。
     */
    private Integer snapTimeoutMs = 10000;
    /**
     * 是否启用 TS
     */
    private Integer enableTs = 1;
    /**
     * 是否启用 HLS
     */
    private Integer enableHls = 0;
    /**
     * 是否启用 FMP4
     */
    private Integer enableFmp4 = 0;
    /**
     * 是否启用 RTSP
     */
    private Integer enableRtsp = 1;
    /**
     * 是否启用 RTMP
     */
    private Integer enableRtmp = 1;
    /**
     * 是否启用 MP4
     */
    private Integer enableMp4 = 0;
    /**
     * 是否启用 HLS FMP4
     */
    private Integer enableHlsFmp4 = 0;
    /**
     * 是否启用音频
     */
    private Integer enableAudio = 1;
    /**
     * 是否将mp4录制当做观看者
     */
    private Integer mp4AsPlayer = 0;
    /**
     * mp4切片大小，单位秒
     */
    private Integer mp4MaxSecond = 3600;
    /**
     * MP4保存路径
     */
    private String mp4SavePath = "./www";
    /**
     * HLS保存路径
     */
    private String hlsSavePath = "./www";
    /**
     * 根目录
     */
    private String rootPath = "./www";
    /**
     * 按需拉取配置(为0和1都可以播放,只是为1时对于第一个播放的用户体验不是很好,但是节省资源)
     */
    private Integer hlsDemand = 1;
    /**
     * 按需拉取配置(为0和1都可以播放,只是为1时对于第一个播放的用户体验不是很好,但是节省资源)
     */
    private Integer rtspDemand = 1;
    /**
     * 按需拉取配置(为0和1都可以播放,只是为1时对于第一个播放的用户体验不是很好,但是节省资源)
     */
    private Integer rtmpDemand = 1;
    /**
     * 按需拉取配置(为0和1都可以播放,只是为1时对于第一个播放的用户体验不是很好,但是节省资源)
     */
    private Integer tsDemand = 0;
    /**
     * 按需拉取配置(为0和1都可以播放,只是为1时对于第一个播放的用户体验不是很好,但是节省资源)
     */
    private Integer fmp4Demand = 1;
    /**
     * 日志等级 0：TRACE 1：DEBUG 2：INFO 3：WARN 4：ERROR
     */
    private Integer logLevel = 2;
    /**
     * 日志输入 1：LOG_CONSOLE输出到控制台  2：LOG_FILE输入到文件 4：LOG_CALLBACK输出到回调函数
     */
    private Integer logMask = 1;
    /**
     * 文件日志保存天数,设置为0关闭日志文件
     */
    private Integer logFileDays = 1;
    /**
     * 文件日志保存路径,路径可以不存在(内部可以创建文件夹)，设置为NULL关闭日志输出至文件
     */
    private String logPath = "./www/logs";
    /**
     * RTC服务器地址
     */
    private String rtcHost = "127.0.0.1";
    /**
     * 是否开启录像
     */
    private Integer broadcastRecordTs = 0;
    /**
     * 分段时长
     */
    private Integer hlsSegDur = 2;
    /**
     * 分段数量
     */
    private Integer hlsSegNum = 3;
    /**
     * MP4点播(rtsp/rtmp/http-flv/ws-flv)是否循环播放文件
     */
    private Integer recordFileRepeat = 1;
    /**
     * 是否启用HTTPS
     */
    private Boolean enableHttps = false;

    /**
     * 原生媒体服务监听地址。
     */
    private String listenIp = "127.0.0.1";

    /**
     * 是否允许未认证播放。
     */
    private Boolean allowAnonymousPlay = false;

    /**
     * 是否允许未认证推流。
     */
    private Boolean allowAnonymousPublish = false;

    /**
     * 获取不指定时，默认开启的协议
     *
     * @return 协议列表
     */
    public Set<SchemeEnum> getEnabledSchemes() {
        Set<SchemeEnum> result = new HashSet<>();
        if (1 == enableRtsp) {
            result.add(RTSP);
        }
        if (1 == enableRtmp) {
            result.add(RTMP);
        }
        if (1 == enableHls) {
            result.add(HLS);
        }
        if (1 == enableFmp4) {
            result.add(FMP4);
        }
        if (1 == enableTs) {
            result.add(TS);
        }
        if (1 == enableMp4) {
            result.add(MP4);
        }
        if (1 == enableHlsFmp4) {
            result.add(HLS_FMP4);
        }
        return result;
    }
}

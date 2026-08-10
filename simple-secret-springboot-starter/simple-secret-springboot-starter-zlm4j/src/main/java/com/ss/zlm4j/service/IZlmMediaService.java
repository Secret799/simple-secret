package com.ss.zlm4j.service;

import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.service.domain.bo.*;
import com.ss.zlm4j.service.domain.vo.RtpServerVo;
import com.ss.zlm4j.service.domain.vo.StatisticVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 媒体服务接口
 *
 * @author JunPzx
 * @since 2024/6/12 14:43
 */
public interface IZlmMediaService {

    /**
     * 根据APP和stream获取流Key
     *
     * @param app    app
     * @param stream stream
     * @param type   pull:拉流，push:推流
     * @return key
     */
    String getStreamKey(String type, String app, String stream);

    /**
     * 添加拉流代理
     *
     * @param param 代理信息
     * @return 代理Key or 错误信息
     */
    String addStreamPullerProxy(@Valid StreamProxyPullerBO param);

    /**
     * 删除拉流代理
     *
     * @param key 代理Key
     * @return 是否删除成功
     */
    Boolean delStreamPullerProxy(@Valid @NotBlank(message = "代理key不能为空") String key);

    /**
     * 添加推流代理
     *
     * @param param 转推流代理信息
     * @return 代理key or 错误信息
     */
    String addStreamPusherProxy(StreamProxyPusherBO param);

    /**
     * 删除推流代理
     *
     * @param key 代理key
     * @return 是否删除成功
     */
    Boolean delStreamPusherProxy(@Valid @NotBlank(message = "代理key不能为空") String key);


    /**
     * 关闭流
     *
     * @param param 关闭流信息
     * @return 是否关闭成功
     */
    Boolean closeStream(@Valid CloseStreamBO param);

    /**
     * 关闭流
     *
     * @param param 关闭流信息
     * @return 结果数组result[0]为关闭失败流数量, result[1]为关闭成功流数量
     */
    Integer[] closeStreams(@Valid CloseStreamsBO param);

    /**
     * 获取流列表
     *
     * @param param 获取流列表信息
     * @return 流列表
     */
    List<MediaSourceDomain> getMediaList(@Valid GetMediaListBO param);

    /**
     * 判断流是否在线
     *
     * @param param 流信息
     * @return 是否在线
     */
    Boolean isMediaOnline(@Valid MediaQueryBO param);

    /**
     * 获取流信息
     *
     * @param param 流信息
     * @return 流信息
     */
    MediaSourceDomain getMediaInfo(@Valid MediaQueryBO param);

    /**
     * 获取流信息
     *
     * @param streamKey 流key
     * @return 流信息
     */
    List<MediaSourceDomain> getMediaList(String streamKey);

    /**
     * 开始录像
     *
     * @param param 录像信息
     * @return 是否开始成功
     */
    Boolean startRecord(@Valid StartRecordBO param);

    /**
     * 停止录像
     *
     * @param param 录像信息
     * @return 是否停止成功
     */
    Boolean stopRecord(@Valid StopRecordBO param);

    /**
     * 判断是否正在录像
     *
     * @param param 录像信息
     * @return 是否正在录像
     */
    Boolean isRecording(@Valid RecordStatusBO param);

    /**
     * 获取服务器内存状态信息
     *
     * @return 服务器内存状态信息
     */
    StatisticVo getStatistic();

    /**
     * 获取流媒体服务配置信息
     *
     * @return 流媒体服务配置信息
     */
    String getServerConfig();

    /**
     * 重启流媒体服务
     *
     * @return 是否重启成功
     */
    Boolean restartServer();

    /**
     * 设置流媒体服务配置信息
     *
     * @param parameterMap 配置信息
     * @return 是否设置成功
     */
    Integer setServerConfig(Map<String, String[]> parameterMap);

    /**
     * 开启RTP服务
     *
     * @param param RTP服务信息
     * @return RTP服务端口号, 如果开启失败则为-1
     */
    Integer openRtpServer(@Valid OpenRtpServerBO param);

    /**
     * 关闭RTP服务
     *
     * @param stream 流id
     * @return 是否关闭成功
     */
    Boolean closeRtpServer(@Valid @NotBlank String stream);

    /**
     * 获取RTP服务列表
     *
     * @return RTP服务列表
     */
    List<RtpServerVo> listRtpServer();
}

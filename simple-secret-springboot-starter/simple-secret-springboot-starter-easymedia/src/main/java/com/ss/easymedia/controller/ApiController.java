package com.ss.easymedia.controller;

import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.service.ISnapService;
import com.ss.zlm4j.service.ITranscodeService;
import com.ss.zlm4j.service.IVideoStackService;
import com.ss.zlm4j.service.IZlmMediaService;
import com.ss.zlm4j.service.domain.bo.*;
import com.ss.zlm4j.service.domain.vo.RtpServerVo;
import com.ss.zlm4j.service.domain.vo.StatisticVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流媒体API
 *
 * @author lidaofu
 * @since 2023/11/29
 **/
@RestController
@RequestMapping("/easyMedia/api/common")
@Validated
@ConditionalOnProperty(name = {
        "simple-secret.zlm4j.enabled",
        "simple-secret.easymedia.enabled",
        "simple-secret.easymedia.management-api-enabled"
}, havingValue = "true")
public class ApiController {

    private final IZlmMediaService iApiService;
    private final ISnapService iSnapService;
    private final ITranscodeService iTranscodeService;
    private final IVideoStackService iVideoStackService;

    /**
     * 创建并初始化实例。
     *
     * @param iApiService ZLMediaKit 通用 API 服务
     * @param iSnapService 截图服务
     * @param iTranscodeService 转码服务
     * @param iVideoStackService 视频拼接服务
     */
    public ApiController(IZlmMediaService iApiService,
                         ISnapService iSnapService,
                         ITranscodeService iTranscodeService,
                         IVideoStackService iVideoStackService) {
        this.iApiService = iApiService;
        this.iSnapService = iSnapService;
        this.iTranscodeService = iTranscodeService;
        this.iVideoStackService = iVideoStackService;
    }

    /**
     * 【拉流代理】添加rtmp/rtsp拉流代理
     * <p>
     * 此接口不会返回具体流地址，请按照流地址生成规则结合自己网络信息来拼接具体地址
     *
     * @return 代理信息

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/addStreamPullerProxy")
    public String addStreamPullerProxy(@Validated @RequestBody StreamProxyPullerBO param) {
        return iApiService.addStreamPullerProxy(param);
    }

    /**
     * 【拉流代理】根据Key关闭拉流代理
     * <p>
     * 流注册成功后，也可以使用close_streams接口替代
     *
     * @return 删除结果

     *
     * @param key 键
     */
    @PostMapping(value = "/delStreamPullerProxyByKey")
    public Boolean delStreamPullerProxyByKey(String key) {
        return iApiService.delStreamPullerProxy(key);
    }

    /**
     * 【拉流代理】关闭拉流代理
     * <p>
     * 流注册成功后，也可以使用close_streams接口替代
     * <p>
     *
     * @param app    app
     * @param stream stream
     * @return 删除结果
     */
    @PostMapping(value = "/delStreamPullerProxy")
    public Boolean delStreamPullerProxy(@NotBlank(message = "app不能为空") String app, @NotBlank(message = "stream不能为空") String stream) {
        String streamKey = iApiService.getStreamKey("pull", app, stream);
        return iApiService.delStreamPullerProxy(streamKey);
    }


    /**
     * 【推流代理】添加rtmp/rtsp推流代理
     *
     * @return 错误信息

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/addStreamPusherProxy")
    public String addStreamPusherProxy(@Validated @RequestBody StreamProxyPusherBO param) {
        return iApiService.addStreamPusherProxy(param);
    }

    /**
     * 【推流代理】根据Key关闭rtmp/rtsp推流代理
     *
     * @param key stream key
     * @return 错误信息
     */
    @PostMapping(value = "/delStreamPusherProxyByKey")
    public Boolean delStreamPusherProxyByKey(String key) {
        return iApiService.delStreamPusherProxy(key);
    }

    /**
     * 【推流代理】关闭rtmp/rtsp推流代理
     *
     * @param app    app
     * @param stream stream
     * @return 错误信息
     */
    @PostMapping(value = "/delStreamPusherProxy")
    public Boolean delStreamPusherProxy(@NotBlank(message = "app不能为空") String app, @NotBlank(message = "stream不能为空") String stream) {
        String streamKey = iApiService.getStreamKey("push", app, stream);
        return iApiService.delStreamPusherProxy(streamKey);
    }


    /**
     * 【流操作】关闭流
     *
     * @return 关闭结果

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/closeStream")
    public Boolean closeStream(@Validated @RequestBody CloseStreamBO param) {
        return iApiService.closeStream(param);
    }

    /**
     * 【流操作】关闭流(批量关)
     *
     * @return 关闭结果

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/closeStreams")
    public String closeStreams(@Validated @RequestBody CloseStreamsBO param) {
        Integer[] result = iApiService.closeStreams(param);
        return String.format("关闭流失败数量:%s,关闭流成功数量:%s", result[0], result[1]);
    }


    /**
     * 【流操作】获取所有流信息
     *
     * @return 流列表
     */
    @GetMapping(value = "/getAllMedia")
    public List<MediaSourceDomain> getAllMedia() {
        return iApiService.getMediaList(new GetMediaListBO());
    }


    /**
     * 【流操作】获取流列表
     *
     * @return 流列表

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/getMediaList")
    public List<MediaSourceDomain> getMediaList(@Validated @RequestBody GetMediaListBO param) {
        return iApiService.getMediaList(param);
    }

    /**
     * 【流操作】获取流信息
     *
     * @return 流信息

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/getMediaInfo")
    public MediaSourceDomain getMediaInfo(@Validated @RequestBody MediaQueryBO param) {
        return iApiService.getMediaInfo(param);
    }

    /**
     * 【流操作】流是否在线
     *
     * @return 是否在线

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/isMediaOnline")
    public Boolean isMediaOnline(@Validated @RequestBody MediaQueryBO param) {
        return iApiService.isMediaOnline(param);
    }

    /**
     * 【录像】开始录像
     *
     * @return 开始结果

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/startRecord")
    public Boolean startRecord(@Validated @RequestBody StartRecordBO param) {
        return iApiService.startRecord(param);
    }

    /**
     * 【录像】停止录像
     *
     * @return 停止结果

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/stopRecord")
    public Boolean stopRecord(@Validated @RequestBody StopRecordBO param) {
        return iApiService.stopRecord(param);
    }

    /**
     * 【录像】是否录像
     *
     * @return 是否录像

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/isRecording")
    public Boolean isRecording(@Validated @RequestBody RecordStatusBO param) {
        return iApiService.isRecording(param);
    }

    /**
     * 【系统】获取内存资源信息
     *
     * @return 录像列表
     */
    @PostMapping(value = "/getStatistic")
    public StatisticVo getStatistic() {
        return iApiService.getStatistic();
    }

    /**
     * 【系统】获取服务器配置
     *
     * @return 录像列表
     */
    @PostMapping(value = "/getServerConfig")
    public String getServerConfig() {
        return iApiService.getServerConfig();
    }

    /**
     * 【系统】重启流媒体服务
     *
     * @return 录像列表
     */
    @PostMapping(value = "/restartServer")
    public Boolean restartServer() {
        return iApiService.restartServer();
    }

    /**
     * 【系统】设置服务器配置
     *
     * @return 录像列表

     *
     * @param request 请求对象
     */
    @PostMapping(value = "/setServerConfig")
    public Integer setServerConfig(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        return iApiService.setServerConfig(parameterMap);
    }

    /**
     * 【RTP服务】开启rtp服务
     *
     * @return 端口

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/openRtpServer")
    public Integer openRtpServer(@Validated @RequestBody OpenRtpServerBO param) {
        return iApiService.openRtpServer(param);
    }

    /**
     * 【RTP服务】关闭rtp服务
     *
     * @return 端口

     *
     * @param stream 媒体流标识
     */
    @PostMapping(value = "/closeRtpServer")
    public Boolean closeRtpServer(@NotBlank(message = "流id不为空")
                                  @RequestParam(value = "stream") String stream) {
        return iApiService.closeRtpServer(stream);
    }

    /**
     * 【RTP服务】获取所有RTP服务器
     *
     * @return 端口
     */
    @PostMapping(value = "/listRtpServer")
    public List<RtpServerVo> listRtpServer() {
        return iApiService.listRtpServer();
    }

    /**
     * 【截图】获取截图
     *
     * @param url 流地址

     *
     * @return {@code snapByUrl}
     */
    @GetMapping(value = "/getSnapByUrl")
    public String getSnapByUrl(String url) {
        return iSnapService.snapToBase64(url);
    }

    /**
     * 【转码】拉流代理转码(beta)
     * <p>
     * 默认H265转H264 支持分辨率调整 暂时只支持视频转码，音频因为各种封装格式对编码格式、音频参数等转换规则复杂暂时不支持

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/transcode")
    public void transcode(@Validated @RequestBody TranscodeBO param) {
        iTranscodeService.transcode(param);
    }

    /**
     * 【拼接屏】开启拼接屏(beta)

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/stack/start")
    public void startStack(@RequestBody @Validated VideoStackBO param) {
        iVideoStackService.startStack(param);
    }

    /**
     * 【拼接屏】重新设置拼接屏(beta)

     *
     * @param param 调用参数
     */
    @PostMapping(value = "/stack/reset")
    public void resetStack(@RequestBody @Validated VideoStackBO param) {
        iVideoStackService.resetStack(param);
    }

    /**
     * 【拼接屏】关闭拼接屏(beta)

     *
     * @param id 唯一标识
     */
    @PostMapping(value = "/stack/stop")
    public void stopStack(@NotBlank(message = "拼接屏任务id不为空") @RequestParam(value = "id") String id) {
        iVideoStackService.stopStack(id);
    }
}

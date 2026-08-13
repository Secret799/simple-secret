package com.ss.zlm4j.context;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_EVENTS;
import com.aizuda.zlm4j.structure.MK_INI;
import com.ss.zlm4j.callback.*;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.handler.impl.*;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.sun.jna.Native;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * zlm4j 媒体全生命周期上下文
 *
 * @author JunPzx
 * @since 2024/6/12 13:45
 */
@Slf4j
public class ZlmMediaContext {


    /**
     * ZLMediaKit 默认配置项。
     */
    @Getter
    private final ZlmMediaProperties defaultProperties;
    /**
     * ZLMediaKit 回调处理器上下文。
     */
    private final ZlmCallbackHandlerContext handlerContext;
    /**
     * ZLMediaKit 默认 INI 配置文本。
     */
    @Getter
    public final Map<String, Object> DEFAULT_ZLM_INI_CONFIGURATION = new HashMap<>();

    /**
     * 创建并初始化实例。
     *
     * @param zlmMediaProperties ZLMediaKit 配置
     * @param callbackHandlerContext ZLMediaKit 回调处理器上下文
     */
    public ZlmMediaContext(ZlmMediaProperties zlmMediaProperties, ZlmCallbackHandlerContext callbackHandlerContext) {
        this.defaultProperties = zlmMediaProperties;
        this.handlerContext = callbackHandlerContext;
    }

    /**
     * 创建并初始化实例。
     *
     * @param zlmMediaProperties ZLMediaKit 配置
     * @param callbackHandlerContext ZLMediaKit 回调处理器上下文
     * @param defaultConfig ZLMediaKit 默认 INI 配置
     */
    public ZlmMediaContext(ZlmMediaProperties zlmMediaProperties, ZlmCallbackHandlerContext callbackHandlerContext, Map<String, Object> defaultConfig) {
        this(zlmMediaProperties, callbackHandlerContext);
        this.DEFAULT_ZLM_INI_CONFIGURATION.putAll(defaultConfig);
    }

    ZlmMediaContext(ZlmMediaProperties zlmMediaProperties, ZlmCallbackHandlerContext callbackHandlerContext, ZLMApi zlmApi) {
        this(zlmMediaProperties, callbackHandlerContext);
        this.zlmApi = zlmApi;
    }

    /**
     * zlm4j api
     */
    @Getter
    private ZLMApi zlmApi;
    /**
     * zlm4j 事件
     */
    @Getter
    private MK_EVENTS mkEvents;
    /**
     * zlm4j 配置
     */
    @Getter
    private MK_INI mkIni;

    /**
     * 初始化 ZLMediaKit 原生环境、INI 配置和媒体服务。
     */
    @PostConstruct
    public void initMediaServer() {
        if (zlmApi == null) {
            zlmApi = loadZlmApi();
        }
        this.initMediaServerConf();
        this.initEvents();
        if (this.startMediaServer()) {
            log.info("【SimpleSecretZLMediaKit】初始化MediaServer程序成功");
            return;
        }
        log.error("【SimpleSecretZLMediaKit】初始化MediaServer程序失败");
        throw new IllegalStateException("MediaServer initialization failed");
    }

    /**
     * 加载{@code zlmApi}。
     *
     * @return 返回的 {@code ZLMApi} 结果
     */
    protected ZLMApi loadZlmApi() {
        return Native.load("mk_api", ZLMApi.class);
    }


    /**
     * 初始化服务器配置
     */
    public void initMediaServerConf() {
        //初始化环境配置
        mkIni = zlmApi.mk_ini_default();
        // 设置配置项
        ZlmMediaHelper.Configurator.setConfig(zlmApi, mkIni, defaultProperties);
        //初始化zmk服务器
        zlmApi.mk_env_init2(defaultProperties.getThreadNum(), defaultProperties.getLogLevel(), defaultProperties.getLogMask(),
                defaultProperties.getLogPath(), defaultProperties.getLogFileDays(), 0, null, 0, null, null);
    }


    /**
     * 初始化事件回调
     */
    public void initEvents() {
        //全局回调
        mkEvents = new MK_EVENTS();
        mkEvents.on_mk_flow_report = initCallback(ZlmCallbackHandlerContext::getFlowReportHandler, MKFlowReportCallBack::new, new DefaultFlowReportHandler());
        mkEvents.on_mk_http_access = initCallback(ZlmCallbackHandlerContext::getHttpAccessHandler, MKHttpAccessCallBack::new, new DefaultHttpAccessHandler());
        mkEvents.on_mk_http_before_access = initCallback(ZlmCallbackHandlerContext::getHttpBeforeAccessHandler, MKHttpBeforeAccessCallBack::new, new DefaultHttpBeforeAccessHandler());
        mkEvents.on_mk_http_request = initCallback(ZlmCallbackHandlerContext::getHttpRequestHandler, MKHttpRequestCallBack::new, new DefaultHttpRequestHandler());
        mkEvents.on_mk_log = new MKLogCallBack();
        mkEvents.on_mk_media_not_found = initCallback(ZlmCallbackHandlerContext::getStreamNoFoundHandler, MKStreamNoFoundCallBack::new, new DefaultStreamNoFoundHandler());
        mkEvents.on_mk_media_no_reader = initCallback(ZlmCallbackHandlerContext::getStreamNoReaderHandler, MKNoReaderCallBack::new, new DefaultStreamNoReaderHandler());
        mkEvents.on_mk_media_play = initCallback(ZlmCallbackHandlerContext::getStreamPlayHandler, MKPlayCallBack::new,
                new DefaultStreamPlayHandler(defaultProperties, zlmApi));
        mkEvents.on_mk_media_publish = initCallback(ZlmCallbackHandlerContext::getStreamPublishHandler, MKPublishCallBack::new,
                new DefaultStreamPublishHandler(defaultProperties, zlmApi));
        mkEvents.on_mk_record_mp4 = initCallback(ZlmCallbackHandlerContext::getRecordMp4Handler, MKRecordMp4CallBack::new, new DefaultRecordMp4Handler());
        mkEvents.on_mk_record_ts = initCallback(ZlmCallbackHandlerContext::getRecordTsHandler, MKRecordTsCallBack::new, new DefaultRecordTsHandler());
        mkEvents.on_mk_media_changed = initCallback(ZlmCallbackHandlerContext::getStreamChangeHandler, MKStreamChangeCallBack::new, new DefaultStreamChangeHandler());
        //添加全局回调
        zlmApi.mk_events_listen(mkEvents);
    }


    /**
     * 初始化回调
     *
     * @param getFunction    处理器上下文中对应处理器的get方法
     * @param creator        创建回调的构造方法
     * @param defaultHandler 默认处理器
     * @param <H>            处理器类型
     * @param <C>            回调类型
     * @return 回调
     */
    private <H, C> C initCallback(Function<ZlmCallbackHandlerContext, H> getFunction, Function<H, C> creator, H defaultHandler) {
        H handler = getFunction.apply(handlerContext);
        return creator.apply(handler != null ? handler : defaultHandler);
    }


    /**
     * 启动流媒体服务器

     *
     * @return 返回的 {@code boolean} 结果
     */
    public boolean startMediaServer() {
        if (!hasEnabledListener()) {
            log.error("【SimpleSecretZLMediaKit】至少需要启用一个原生媒体监听器");
            return false;
        }
        boolean httpStarted = startHttpListener();
        boolean rtspStarted = startRtspListener();
        boolean rtmpStarted = startRtmpListener();
        boolean rtcStarted = startRtcListener();
        boolean allStarted = httpStarted && rtspStarted && rtmpStarted && rtcStarted;
        if (!allStarted) {
            zlmApi.mk_stop_all_server();
        }
        return allStarted;
    }

    /**
     * 判断是否至少启用了一个原生监听器。
     *
     * @return 至少启用一个监听器时返回 true
     */
    private boolean hasEnabledListener() {
        return Boolean.TRUE.equals(defaultProperties.getHttpListenerEnabled())
                || Boolean.TRUE.equals(defaultProperties.getRtspListenerEnabled())
                || Boolean.TRUE.equals(defaultProperties.getRtmpListenerEnabled())
                || Boolean.TRUE.equals(defaultProperties.getRtcListenerEnabled());
    }

    /**
     * 按配置启动原生 HTTP 监听器。
     *
     * @return 监听器已禁用或启动成功时返回 true
     */
    private boolean startHttpListener() {
        if (!Boolean.TRUE.equals(defaultProperties.getHttpListenerEnabled())) {
            log.info("【SimpleSecretZLMediaKit】HTTP流媒体监听器已禁用，跳过启动");
            return true;
        }
        short port = zlmApi.mk_http_server_start(defaultProperties.getHttpPort().shortValue(), 0);
        return logListenerResult("HTTP", port);
    }

    /**
     * 按配置启动原生 RTSP 监听器。
     *
     * @return 监听器已禁用或启动成功时返回 true
     */
    private boolean startRtspListener() {
        if (!Boolean.TRUE.equals(defaultProperties.getRtspListenerEnabled())) {
            log.info("【SimpleSecretZLMediaKit】RTSP流媒体监听器已禁用，跳过启动");
            return true;
        }
        short port = zlmApi.mk_rtsp_server_start(defaultProperties.getRtspPort().shortValue(), 0);
        return logListenerResult("RTSP", port);
    }

    /**
     * 按配置启动原生 RTMP 监听器。
     *
     * @return 监听器已禁用或启动成功时返回 true
     */
    private boolean startRtmpListener() {
        if (!Boolean.TRUE.equals(defaultProperties.getRtmpListenerEnabled())) {
            log.info("【SimpleSecretZLMediaKit】RTMP流媒体监听器已禁用，跳过启动");
            return true;
        }
        short port = zlmApi.mk_rtmp_server_start(defaultProperties.getRtmpPort().shortValue(), 0);
        return logListenerResult("RTMP", port);
    }

    /**
     * 按配置启动原生 RTC 监听器。
     *
     * @return 监听器已禁用或启动成功时返回 true
     */
    private boolean startRtcListener() {
        if (!Boolean.TRUE.equals(defaultProperties.getRtcListenerEnabled())) {
            log.info("【SimpleSecretZLMediaKit】RTC流媒体监听器已禁用，跳过启动");
            return true;
        }
        short port = zlmApi.mk_rtc_server_start(defaultProperties.getRtcPort().shortValue());
        return logListenerResult("RTC", port);
    }

    /**
     * 记录监听器启动结果。
     *
     * @param protocol 监听协议
     * @param port 原生接口返回的实际端口，0 表示失败
     * @return 端口非 0 时返回 true
     */
    private boolean logListenerResult(String protocol, short port) {
        if (port == 0) {
            log.error("【SimpleSecretZLMediaKit】{}流媒体监听器启动失败", protocol);
            return false;
        }
        log.info("【SimpleSecretZLMediaKit】{}流媒体监听器启动成功，端口：{}", protocol, port);
        return true;
    }

    /**
     * 关闭流媒体服务器
     */
    public void stopMediaServer() {
        zlmApi.mk_stop_all_server();
        log.info("【SimpleSecretZLMediaKit】关闭所有流媒体服务");
    }

    /**
     * 释放资源
     */
    @PreDestroy
    public void release() {
        this.stopMediaServer();
    }
}

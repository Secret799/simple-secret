package com.ss.zlm4j.service.impl;

import com.aizuda.zlm4j.callback.IMKGetStatisticCallBack;
import com.aizuda.zlm4j.callback.IMKProxyPlayerCallBack;
import com.aizuda.zlm4j.callback.IMKPushEventCallBack;
import com.aizuda.zlm4j.callback.IMKRtpServerDetachCallBack;
import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.*;
import com.ss.zlm4j.callback.MKSourceFindCallBack;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.constants.ZlmMediaServerConstants;
import com.ss.zlm4j.context.ZlmMediaContext;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.enums.SchemeEnum;
import com.ss.zlm4j.exception.ZlmOperationException;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.security.MediaResourceUsage;
import com.ss.zlm4j.service.IZlmMediaService;
import com.ss.zlm4j.service.domain.bo.*;
import com.ss.zlm4j.service.domain.vo.RtpServerVo;
import com.ss.zlm4j.service.domain.vo.StatisticVo;
import com.sun.jna.Pointer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


/**
 * zlm4j 简单媒体API 服务实现
 *
 * @author JunPzx
 * @since 2024/6/12 15:54
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "simple-secret.zlm4j.enabled", havingValue = "true")
public class ZlmMediaServiceImpl implements IZlmMediaService {

    private final MediaResourcePolicy mediaResourcePolicy;
    private final ZLMApi zlmApi;

    /**
     * 创建并初始化实例。
     *
     * @param mediaResourcePolicy 媒体资源访问策略
     */
    public ZlmMediaServiceImpl(MediaResourcePolicy mediaResourcePolicy) {
        this(mediaResourcePolicy, (ZLMApi) null);
    }

    ZlmMediaServiceImpl(MediaResourcePolicy mediaResourcePolicy, ZLMApi zlmApi) {
        this.mediaResourcePolicy = mediaResourcePolicy;
        this.zlmApi = zlmApi;
    }

    /**
     * 创建并初始化实例。
     *
     * @param mediaResourcePolicy 媒体资源访问策略
     * @param context 调用上下文
     */
    @Autowired
    public ZlmMediaServiceImpl(MediaResourcePolicy mediaResourcePolicy, ZlmMediaContext context) {
        this(mediaResourcePolicy, context.getZlmApi());
    }

    /**
     * 拉流代理列表
     */
    private final ConcurrentMap<String, MK_PROXY_PLAYER> proxyPlayers = new ConcurrentHashMap<>();
    /**
     * 推流代理列表
     */
    private final ConcurrentMap<String, MK_PUSHER> pushers = new ConcurrentHashMap<>();
    /**
     * 拉流代理关闭回调
     */
    private final ConcurrentMap<String, IMKProxyPlayerCallBack> proxyPlayerCallbacks = new ConcurrentHashMap<>();
    /** 拉流首次结果回调，避免异步完成前被 GC。 */
    private final ConcurrentMap<String, IMKProxyPlayerCallBack> proxyResultCallbacks = new ConcurrentHashMap<>();
    /**
     * 推流代理关闭回调
     */
    private final ConcurrentMap<String, IMKPushEventCallBack> pusherCallbacks = new ConcurrentHashMap<>();
    /** 推流首次结果回调，避免异步完成前被 GC。 */
    private final ConcurrentMap<String, IMKPushEventCallBack> pusherResultCallbacks = new ConcurrentHashMap<>();
    /**
     * rtp服务列表
     */
    private final ConcurrentMap<String, MK_RTP_SERVER> rtpServers = new ConcurrentHashMap<>();
    /**
     * rtp服务断开回调
     */
    private final ConcurrentMap<String, IMKRtpServerDetachCallBack> rtpDetachCallbacks = new ConcurrentHashMap<>();

    /**
     * stream key的分隔符
     */
    private static final String STREAM_KEY_TEMPLATE = "{}_{}_{}";

    @Override
    public String getStreamKey(String type, String app, String stream) {
        return Base64.getEncoder().encodeToString(
                MessageFormatter.basicArrayFormat(STREAM_KEY_TEMPLATE, new Object[]{type, app, stream})
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String addStreamPullerProxy(StreamProxyPullerBO param) {
        URI sourceUri = mediaResourcePolicy.requireAllowed(param.getUrl(), MediaResourceUsage.PULL);
        String sourceUrl = sourceUri.toASCIIString();
        //查询流是是否存在
        // todo后续兼容param中enable控制
        Set<SchemeEnum> enabledSchemes = (param.getSchema() == null || param.getSchema().isBlank()) ?
                ZlmMediaHelper.getContext().getDefaultProperties().getEnabledSchemes() : SchemeEnum.listByCodes(param.getSchema());
        Set<SchemeEnum> existSchemes = getMediaList(new GetMediaListBO()
                .setStream(param.getStream()).setApp(param.getApp()))
                .stream().map(MediaSourceDomain::getSchema)
                .map(SchemeEnum::getByCode)
                .collect(Collectors.toSet());
        if (existSchemes.containsAll(enabledSchemes)) {
            throw new IllegalStateException("当前流信息已被使用");
        }
        //创建拉流代理
        MK_INI option = api().mk_ini_create();
        MK_PROXY_PLAYER mkProxy;
        try {
            ZlmMediaHelper.Configurator.setOrDefaultConfig(option, "enable_mp4", param.getEnableMp4(), ZlmMediaProperties::getEnableMp4);
            ZlmMediaHelper.Configurator.setOrDefaultConfig(option, "enable_audio", param.getEnableAudio(), ZlmMediaProperties::getEnableAudio);
            ZlmMediaHelper.Configurator.setOrDefaultConfig(option, "enable_fmp4", param.getEnableFmp4(), ZlmMediaProperties::getEnableFmp4);
            ZlmMediaHelper.Configurator.setOrDefaultConfig(option, "enable_ts", param.getEnableTs(), ZlmMediaProperties::getEnableTs);
            ZlmMediaHelper.Configurator.setOrDefaultConfig(option, "enable_hls", param.getEnableHls(), ZlmMediaProperties::getEnableHls);
            ZlmMediaHelper.Configurator.setOrDefaultConfig(option, "enable_rtsp", param.getEnableRtsp(), ZlmMediaProperties::getEnableRtsp);
            ZlmMediaHelper.Configurator.setOrDefaultConfig(option, "enable_rtmp", param.getEnableRtmp(), ZlmMediaProperties::getEnableRtmp);
            ZlmMediaHelper.Configurator.setOrDefaultConfig(option, "mp4_max_second", param.getMp4MaxSecond(), ZlmMediaProperties::getMp4MaxSecond);
            ZlmMediaHelper.Configurator.setConfig(option, "add_mute_audio", 0);
            ZlmMediaHelper.Configurator.setOrDefaultConfig(option, "auto_close", param.getAutoClose(), ZlmMediaProperties::getAutoClose);
            ZlmMediaHelper.Configurator.setOrDefaultConfig(option, "record.fileRepeat", param.getRecordFileRepeat(), ZlmMediaProperties::getRecordFileRepeat);
            mkProxy = api().mk_proxy_player_create4(ZlmMediaServerConstants.DEFAULT_VHOST,
                    param.getApp(), param.getStream(), option,
                    Optional.ofNullable(param.getRetryCount()).orElse(3));
        } finally {
            api().mk_ini_release(option);
        }
        //设置超时时间
        if (param.getTimeoutSec() != null) {
            api().mk_proxy_player_set_option(mkProxy, "protocol_timeout_ms", String.valueOf(param.getTimeoutSec() * 1000));
        }
        //设置拉流方式
        if (sourceUri.getScheme().startsWith("rtsp") && param.getRtpType() != null) {
            api().mk_proxy_player_set_option(mkProxy, "rtp_type", param.getRtpType().toString());
        }
        // 设置rtsp倍速
        if (param.getRtspSpeed() != null) {
            api().mk_proxy_player_set_option(mkProxy, "rtsp_speed", param.getRtspSpeed()
                    .setScale(2, RoundingMode.HALF_UP).toString());
        }
        String key = getStreamKey("pull", param.getApp(), param.getStream());
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        //第一次代理结果获取
        IMKProxyPlayerCallBack imkProxyPlayerCallBack = (pUser, err, what, sysErr) -> {
            proxyResultCallbacks.remove(key);
            if (err != 0) {
                log.warn("【SimpleSecretZLMediaKit】拉流代理失败：{}", what);
                queue.offer(what);
            } else {
                queue.offer(key);
            }
        };
        //回调关闭事件
        IMKProxyPlayerCallBack imkProxyPlayCloseCallBack = (pUser, err, what, sysErr) -> {
            //这里Pointer是ZLM维护的不需要我们释放 遵循谁申请谁释放原则
            MK_PROXY_PLAYER removed = proxyPlayers.remove(key);
            proxyPlayerCallbacks.remove(key);
            proxyResultCallbacks.remove(key);
            if (removed != null) {
                api().mk_proxy_player_release(removed);
                log.info("【SimpleSecretZLMediaKit】拉流代理关闭");
            }
        };
        MK_PROXY_PLAYER existingProxy = proxyPlayers.putIfAbsent(key, mkProxy);
        if (existingProxy != null) {
            api().mk_proxy_player_release(mkProxy);
            throw new ZlmOperationException("拉流代理已存在");
        }
        proxyResultCallbacks.put(key, imkProxyPlayerCallBack);
        proxyPlayerCallbacks.put(key, imkProxyPlayCloseCallBack);
        api().mk_proxy_player_set_on_play_result(mkProxy, imkProxyPlayerCallBack, mkProxy.getPointer(), null);
        //添加代理关闭回调 并把代理客户端传过去释放
        api().mk_proxy_player_set_on_close(mkProxy, imkProxyPlayCloseCallBack, mkProxy.getPointer());
        //开始播放
        api().mk_proxy_player_play(mkProxy, sourceUrl);
        try {
            return queue.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ZlmOperationException("等待拉流代理结果时线程被中断", exception);
        }
    }

    @Override
    public Boolean delStreamPullerProxy(String key) {
        MK_PROXY_PLAYER mkProxyPlayer = proxyPlayers.remove(key);
        if (mkProxyPlayer != null) {
            proxyPlayerCallbacks.remove(key);
            proxyResultCallbacks.remove(key);
            api().mk_proxy_player_release(mkProxyPlayer);
            return true;
        }
        return false;
    }

    @Override
    public String addStreamPusherProxy(StreamProxyPusherBO param) {
        URI targetUri = mediaResourcePolicy.requireAllowed(param.getUrl(), MediaResourceUsage.PUSH);
        String targetUrl = targetUri.toASCIIString();
        String key = getStreamKey("push", param.getApp(), param.getStream());
        MK_MEDIA_SOURCE mkMediaSource = api()
                .mk_media_source_find2(param.getSchema(), ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getStream(), 0);
        if (mkMediaSource == null) {
            throw new ZlmOperationException(MessageFormatter.basicArrayFormat(
                    "app:{},stream:{},scheme:{}对应的流不存在",
                    new Object[]{param.getApp(), param.getStream(), param.getSchema()}));
        }
        MK_PUSHER mkPusher = api().mk_pusher_create_src(mkMediaSource);
        if (targetUri.getScheme().startsWith("rtsp") && param.getRtpType() != null) {
            api().mk_pusher_set_option(mkPusher, "rtp_type", param.getRtpType().toString());
        }
        if (param.getTimeoutSec() != null) {
            api().mk_pusher_set_option(mkPusher, "protocol_timeout_ms", String.valueOf(param.getTimeoutSec() * 1000));
        }
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        //推流结果回调
        IMKPushEventCallBack resultCallback = (userData, errCode, errMsg) -> {
            pusherResultCallbacks.remove(key);
            if (errCode != 0) {
                queue.offer(errMsg);
                log.warn("【SimpleSecretZLMediaKit】推流代理失败：{}", errMsg);
            } else {
                queue.offer(key);
                log.info("【SimpleSecretZLMediaKit】推流代理成功");
            }
        };
        IMKPushEventCallBack imkPushEventCallBack = (userData, errCode, errMsg) -> {
            MK_PUSHER removed = pushers.remove(key);
            pusherCallbacks.remove(key);
            pusherResultCallbacks.remove(key);
            if (removed != null) {
                api().mk_pusher_release(removed);
                log.info("【SimpleSecretZLMediaKit】推流代理关闭");
            }
        };
        MK_PUSHER existingPusher = pushers.putIfAbsent(key, mkPusher);
        if (existingPusher != null) {
            api().mk_pusher_release(mkPusher);
            throw new ZlmOperationException("推流代理已存在");
        }
        pusherResultCallbacks.put(key, resultCallback);
        pusherCallbacks.put(key, imkPushEventCallBack);
        api().mk_pusher_set_on_result(mkPusher, resultCallback, mkPusher.getPointer());
        //推流关闭回调
        api().mk_pusher_set_on_shutdown(mkPusher, imkPushEventCallBack, mkPusher.getPointer());
        //转推流地址 可以是rtmp或者rtsp
        api().mk_pusher_publish(mkPusher, targetUrl);
        try {
            return queue.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ZlmOperationException("等待推流代理结果时线程被中断", exception);
        }
    }

    @Override
    public Boolean delStreamPusherProxy(String key) {
        MK_PUSHER mkPusher = pushers.remove(key);
        if (mkPusher != null) {
            pusherCallbacks.remove(key);
            pusherResultCallbacks.remove(key);
            api().mk_pusher_release(mkPusher);
            return true;
        }
        return false;
    }

    @Override
    public Boolean closeStream(CloseStreamBO param) {
        //查询流是是否存在
        MK_MEDIA_SOURCE mkMediaSource = api()
                .mk_media_source_find2(param.getSchema(), ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getStream(), 0);
        if (mkMediaSource == null) {
            throw new ZlmOperationException("当前流不在线");
        }
        return api().mk_media_source_close(mkMediaSource, param.getForce()) == 1;
    }

    @Override
    public Integer[] closeStreams(CloseStreamsBO param) {
        Integer[] result = new Integer[]{
                0, 0
        };
        api().mk_media_source_for_each(Pointer.NULL, new MKSourceFindCallBack((MK_MEDIA_SOURCE ctx) -> {
            int status = api().mk_media_source_close(ctx, param.getForce());
            if (status == 0) {
                result[0] += 1;
            } else {
                result[1] += 1;
            }
        }), param.getSchema(), ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getStream());
        return result;
    }

    @Override
    public List<MediaSourceDomain> getMediaList(GetMediaListBO param) {
        List<MediaSourceDomain> list = new ArrayList<>();
        if (param.getStreamKey() != null && !param.getStreamKey().isBlank()) {
            String streamKey = param.getStreamKey();
            String[] honeybees = new String(Base64.getDecoder().decode(streamKey)).split("_");
            if (honeybees.length == 3) {
                param.setApp(honeybees[1]);
                param.setStream(honeybees[2]);
            }
        }
        api().mk_media_source_for_each(Pointer.NULL, new MKSourceFindCallBack((MK_MEDIA_SOURCE ctx) ->
                list.add(ZlmMediaHelper.Assembler.getMediaSource(ctx))), param.getSchema(), ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getStream());
        return list;
    }

    @Override
    public List<MediaSourceDomain> getMediaList(String streamKey) {
        String[] honeybees = new String(Base64.getDecoder().decode(streamKey)).split("_");
        if (honeybees.length != 3) {
            throw new ZlmOperationException("streamKey不合法");
        }
        String app = honeybees[1];
        String stream = honeybees[2];
        return getMediaList(new GetMediaListBO()
                .setApp(app)
                .setStream(stream));
    }

    @Override
    public Boolean isMediaOnline(MediaQueryBO param) {
        MK_MEDIA_SOURCE mkMediaSource = api()
                .mk_media_source_find2(param.getSchema(), ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getStream(), 0);
        return mkMediaSource != null;
    }

    @Override
    public MediaSourceDomain getMediaInfo(MediaQueryBO param) {
        MK_MEDIA_SOURCE mkMediaSource = api()
                .mk_media_source_find2(param.getSchema(), ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getStream(), 0);
        if (mkMediaSource != null) {
            return ZlmMediaHelper.Assembler.getMediaSource(mkMediaSource);
        }
        return null;
    }

    @Override
    public Boolean startRecord(StartRecordBO param) {
        String recordingPath = param.getCustomizedPath();
        if (recordingPath != null && !recordingPath.isBlank()) {
            recordingPath = mediaResourcePolicy.requireRecordingPath(recordingPath).toString();
        }
        int ret = api().mk_recorder_start(param.getType(), ZlmMediaServerConstants.DEFAULT_VHOST,
                param.getApp(), param.getStream(), recordingPath, param.getMaxSecond());
        return ret == 1;
    }

    @Override
    public Boolean stopRecord(StopRecordBO param) {
        int ret = api().mk_recorder_stop(param.getType(), ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getStream());
        return ret == 1;
    }

    @Override
    public Boolean isRecording(RecordStatusBO param) {
        int ret = api().mk_recorder_is_recording(param.getType(), ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getStream());
        return ret == 1;
    }

    @Override
    public StatisticVo getStatistic() {
        StatisticVo statistic = new StatisticVo();
        BlockingQueue<Boolean> queue = new ArrayBlockingQueue<>(1);
        IMKGetStatisticCallBack imkGetStatisticCallBack = (userData, ini) -> {
            String mediaSource = api().mk_ini_get_option(ini, "object.MediaSource");
            String multiMediaSourceMuxer = api().mk_ini_get_option(ini, "object.MultiMediaSourceMuxer");
            String tcpServer = api().mk_ini_get_option(ini, "object.TcpServer");
            String tcpSession = api().mk_ini_get_option(ini, "object.TcpSession");
            String udpServer = api().mk_ini_get_option(ini, "object.UdpServer");
            String udpSession = api().mk_ini_get_option(ini, "object.UdpSession");
            String tcpClient = api().mk_ini_get_option(ini, "object.TcpClient");
            String socket = api().mk_ini_get_option(ini, "object.Socket");
            String frameImp = api().mk_ini_get_option(ini, "object.FrameImp");
            String frame = api().mk_ini_get_option(ini, "object.Frame");
            String buffer = api().mk_ini_get_option(ini, "object.Buffer");
            String bufferRaw = api().mk_ini_get_option(ini, "object.BufferRaw");
            String bufferLikeString = api().mk_ini_get_option(ini, "object.BufferLikeString");
            String bufferList = api().mk_ini_get_option(ini, "object.BufferList");
            String rtpPacket = api().mk_ini_get_option(ini, "object.RtpPacket");
            String rtmpPacket = api().mk_ini_get_option(ini, "object.RtmpPacket");
            statistic.setMediaSource(Long.valueOf(mediaSource));
            statistic.setMultiMediaSourceMuxer(Long.valueOf(multiMediaSourceMuxer));
            statistic.setTcpServer(Long.valueOf(tcpServer));
            statistic.setTcpSession(Long.valueOf(tcpSession));
            statistic.setUdpServer(Long.valueOf(udpServer));
            statistic.setUdpSession(Long.valueOf(udpSession));
            statistic.setTcpClient(Long.valueOf(tcpClient));
            statistic.setSocket(Long.valueOf(socket));
            statistic.setFrameImp(Long.valueOf(frameImp));
            statistic.setFrame(Long.valueOf(frame));
            statistic.setBuffer(Long.valueOf(buffer));
            statistic.setBufferRaw(Long.valueOf(bufferRaw));
            statistic.setBufferLikeString(Long.valueOf(bufferLikeString));
            statistic.setBufferList(Long.valueOf(bufferList));
            statistic.setRtpPacket(Long.valueOf(rtpPacket));
            statistic.setRtmpPacket(Long.valueOf(rtmpPacket));
            queue.offer(true);
        };
        api().mk_get_statistic(imkGetStatisticCallBack, null, user_data -> {
        });
        try {
            queue.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ZlmOperationException("获取服务器统计信息失败", e);
        }
        return statistic;
    }

    @Override
    public String getServerConfig() {
        Pointer pointer = api().mk_ini_dump_string(ZlmMediaHelper.getContext().getMkIni());
        String string = pointer.getString(0);
        api().mk_free(pointer);
        return string;
    }

    @Override
    public Boolean restartServer() {
        ZlmMediaHelper.getContext().stopMediaServer();
        return ZlmMediaHelper.getContext().startMediaServer();
    }

    @Override
    public Integer setServerConfig(Map<String, String[]> parameterMap) {
        AtomicInteger count = new AtomicInteger();
        parameterMap.forEach((key, value) -> {
            if (value[0].matches("\\d+")) {
                api().mk_ini_set_option_int(ZlmMediaHelper.getContext().getMkIni(), key, Integer.parseInt(value[0]));
            } else {
                api().mk_ini_set_option(ZlmMediaHelper.getContext().getMkIni(), key, value[0]);
            }
            count.getAndIncrement();
        });
        return count.get();
    }

    @Override
    public Integer openRtpServer(OpenRtpServerBO param) {
        MK_RTP_SERVER mkRtpServer = api().mk_rtp_server_create(param.getPort().shortValue(), param.getTcpMode(), param.getStream());
        if (mkRtpServer == null) {
            return -1;
        }
        short i = api().mk_rtp_server_port(mkRtpServer);
        //监听rtp服务器断开事件
        IMKRtpServerDetachCallBack imkRtpServerDetachCallBack = userData -> {
            MK_RTP_SERVER removed = rtpServers.remove(param.getStream());
            rtpDetachCallbacks.remove(param.getStream());
            if (removed != null) {
                api().mk_rtp_server_release(removed);
            }
        };
        MK_RTP_SERVER existingServer = rtpServers.putIfAbsent(param.getStream(), mkRtpServer);
        if (existingServer != null) {
            api().mk_rtp_server_release(mkRtpServer);
            return -1;
        }
        rtpDetachCallbacks.put(param.getStream(), imkRtpServerDetachCallBack);
        api().mk_rtp_server_set_on_detach(mkRtpServer, imkRtpServerDetachCallBack, null);
        return (int) i;
    }

    @Override
    public Boolean closeRtpServer(String streamId) {
        MK_RTP_SERVER mkRtpServer = rtpServers.remove(streamId);
        if (mkRtpServer != null) {
            rtpDetachCallbacks.remove(streamId);
            api().mk_rtp_server_release(mkRtpServer);
            return true;
        }
        return false;
    }

    @Override
    public List<RtpServerVo> listRtpServer() {
        List<RtpServerVo> rtpServerResults = new ArrayList<>();
        if (!rtpServers.isEmpty()) {
            rtpServers.forEach((key, value) -> {
                RtpServerVo rtpServerResult = new RtpServerVo();
                rtpServerResult.setPort((int) api().mk_rtp_server_port(value));
                rtpServerResult.setStream(key);
                rtpServerResults.add(rtpServerResult);
            });
        }
        return rtpServerResults;
    }

    /**
     * 释放当前服务实例持有的全部 native 代理和 RTP 服务。
     */
    @PreDestroy
    public void close() {
        proxyPlayers.forEach((key, player) -> {
            if (proxyPlayers.remove(key, player)) {
                api().mk_proxy_player_release(player);
            }
        });
        pushers.forEach((key, pusher) -> {
            if (pushers.remove(key, pusher)) {
                api().mk_pusher_release(pusher);
            }
        });
        rtpServers.forEach((key, server) -> {
            if (rtpServers.remove(key, server)) {
                api().mk_rtp_server_release(server);
            }
        });
        proxyPlayerCallbacks.clear();
        proxyResultCallbacks.clear();
        pusherCallbacks.clear();
        pusherResultCallbacks.clear();
        rtpDetachCallbacks.clear();
    }

    private ZLMApi api() {
        return zlmApi != null ? zlmApi : ZlmMediaHelper.getZlmApi();
    }


}

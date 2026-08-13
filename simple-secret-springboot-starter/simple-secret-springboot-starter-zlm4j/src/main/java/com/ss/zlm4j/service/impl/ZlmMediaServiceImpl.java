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
import java.time.Duration;
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

    /** 推拉流代理首次连接结果的默认等待时间。 */
    private static final Duration DEFAULT_PROXY_STARTUP_TIMEOUT = Duration.ofSeconds(5);

    /** 媒体 URL 与文件路径访问策略。 */
    private final MediaResourcePolicy mediaResourcePolicy;
    /** 当前实例使用的 native API；为空时使用已初始化的全局 API。 */
    private final ZLMApi zlmApi;
    /** 当前实例的 ZLM 默认媒体配置。 */
    private final ZlmMediaProperties defaultProperties;
    /** 推拉流代理等待首次连接结果的最长时间。 */
    private final Duration proxyStartupTimeout;

    /**
     * 创建并初始化实例。
     *
     * @param mediaResourcePolicy 媒体资源访问策略
     */
    public ZlmMediaServiceImpl(MediaResourcePolicy mediaResourcePolicy) {
        this(mediaResourcePolicy, null, new ZlmMediaProperties(), DEFAULT_PROXY_STARTUP_TIMEOUT);
    }

    ZlmMediaServiceImpl(MediaResourcePolicy mediaResourcePolicy, ZLMApi zlmApi) {
        this(mediaResourcePolicy, zlmApi, new ZlmMediaProperties(), DEFAULT_PROXY_STARTUP_TIMEOUT);
    }

    ZlmMediaServiceImpl(MediaResourcePolicy mediaResourcePolicy, ZLMApi zlmApi,
                        ZlmMediaProperties defaultProperties, Duration proxyStartupTimeout) {
        this.mediaResourcePolicy = mediaResourcePolicy;
        this.zlmApi = zlmApi;
        this.defaultProperties = Objects.requireNonNull(defaultProperties, "defaultProperties");
        this.proxyStartupTimeout = Objects.requireNonNull(proxyStartupTimeout, "proxyStartupTimeout");
        if (proxyStartupTimeout.isZero() || proxyStartupTimeout.isNegative()) {
            throw new IllegalArgumentException("proxyStartupTimeout must be positive");
        }
    }

    /**
     * 创建并初始化实例。
     *
     * @param mediaResourcePolicy 媒体资源访问策略
     * @param context 调用上下文
     */
    @Autowired
    public ZlmMediaServiceImpl(MediaResourcePolicy mediaResourcePolicy, ZlmMediaContext context) {
        this(mediaResourcePolicy, context.getZlmApi(), context.getDefaultProperties(),
                DEFAULT_PROXY_STARTUP_TIMEOUT);
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
    /** 正在释放的拉流代理，防止 native 同步回调递归释放。 */
    private final Set<MK_PROXY_PLAYER> releasingPullers = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 尚未注册且首次释放失败的拉流代理，供关闭阶段重试。 */
    private final Set<MK_PROXY_PLAYER> pendingPullerReleases = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 正在释放的推流代理，防止 native 同步回调递归释放。 */
    private final Set<MK_PUSHER> releasingPushers = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 尚未注册且首次释放失败的推流代理，供关闭阶段重试。 */
    private final Set<MK_PUSHER> pendingPusherReleases = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * rtp服务列表
     */
    private final ConcurrentMap<String, MK_RTP_SERVER> rtpServers = new ConcurrentHashMap<>();
    /**
     * rtp服务断开回调
     */
    private final ConcurrentMap<String, IMKRtpServerDetachCallBack> rtpDetachCallbacks = new ConcurrentHashMap<>();
    /** 正在释放的 RTP 服务，防止 native 同步回调递归释放。 */
    private final Set<MK_RTP_SERVER> releasingRtpServers = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 尚未注册且首次释放失败的 RTP 服务，供关闭阶段重试。 */
    private final Set<MK_RTP_SERVER> pendingRtpReleases = Collections.newSetFromMap(new IdentityHashMap<>());
    /** 服务是否已经进入永久关闭状态。 */
    private boolean closed;

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
    public synchronized String addStreamPullerProxy(StreamProxyPullerBO param) {
        requireOpen();
        URI sourceUri = mediaResourcePolicy.requireAllowed(param.getUrl(), MediaResourceUsage.PULL);
        requireAvailablePullerTarget(param);
        MK_PROXY_PLAYER proxyPlayer = createPullerProxy(param, sourceUri);
        String key = getStreamKey("pull", param.getApp(), param.getStream());
        BlockingQueue<PullerStartupResult> resultQueue = new ArrayBlockingQueue<>(1);
        registerPullerCallbacks(key, proxyPlayer, resultQueue);
        try {
            api().mk_proxy_player_play(proxyPlayer, sourceUri.toASCIIString());
            return awaitPullerStartup(key, proxyPlayer, resultQueue);
        } catch (RuntimeException | Error exception) {
            releasePullerAfterFailure(key, proxyPlayer, exception);
            throw exception;
        }
    }

    /**
     * 校验目标流名没有占用已启用协议。
     *
     * @param param 拉流参数
     */
    public void requireAvailablePullerTarget(StreamProxyPullerBO param) {
        Set<SchemeEnum> enabledSchemes = param.getSchema() == null || param.getSchema().isBlank()
                ? defaultProperties.getEnabledSchemes() : SchemeEnum.listByCodes(param.getSchema());
        Set<SchemeEnum> existingSchemes = getMediaList(new GetMediaListBO()
                .setStream(param.getStream()).setApp(param.getApp())).stream()
                .map(MediaSourceDomain::getSchema)
                .map(SchemeEnum::getByCode)
                .collect(Collectors.toSet());
        if (existingSchemes.containsAll(enabledSchemes)) {
            throw new IllegalStateException("当前流信息已被使用");
        }
    }

    /**
     * 创建并配置拉流代理，配置失败时释放 native 资源。
     *
     * @param param 拉流参数
     * @param sourceUri 已校验的源地址
     * @return native 拉流代理
     */
    public MK_PROXY_PLAYER createPullerProxy(StreamProxyPullerBO param, URI sourceUri) {
        MK_INI option = api().mk_ini_create();
        if (option == null) {
            throw new ZlmOperationException("创建拉流代理配置失败");
        }
        MK_PROXY_PLAYER proxyPlayer = null;
        Throwable creationFailure = null;
        try {
            configurePullerOptions(option, param);
            proxyPlayer = api().mk_proxy_player_create4(ZlmMediaServerConstants.DEFAULT_VHOST,
                    param.getApp(), param.getStream(), option,
                    Optional.ofNullable(param.getRetryCount()).orElse(3));
        } catch (RuntimeException | Error exception) {
            creationFailure = exception;
            throw exception;
        } finally {
            releasePullerOption(option, proxyPlayer, creationFailure);
        }
        if (proxyPlayer == null) {
            throw new ZlmOperationException("创建拉流代理失败");
        }
        try {
            configurePullerTransport(proxyPlayer, param, sourceUri);
            return proxyPlayer;
        } catch (RuntimeException | Error exception) {
            releaseUnregisteredPuller(proxyPlayer, exception);
            throw exception;
        }
    }

    /**
     * 注册首次连接和关闭回调。
     *
     * @param key 代理键
     * @param proxyPlayer native 拉流代理
     * @param resultQueue 首次连接结果队列
     */
    public synchronized void registerPullerCallbacks(String key, MK_PROXY_PLAYER proxyPlayer,
                                                     BlockingQueue<PullerStartupResult> resultQueue) {
        IMKProxyPlayerCallBack resultCallback = (pUser, err, what, sysErr) -> {
            resultQueue.offer(err == 0 ? PullerStartupResult.connected()
                    : PullerStartupResult.failed());
        };
        IMKProxyPlayerCallBack closeCallback = (pUser, err, what, sysErr) -> {
            if (releasePuller(key, proxyPlayer)) {
                log.info("【SimpleSecretZLMediaKit】拉流代理关闭");
            }
        };
        MK_PROXY_PLAYER existingProxy = proxyPlayers.putIfAbsent(key, proxyPlayer);
        if (existingProxy != null) {
            ZlmOperationException failure = new ZlmOperationException("拉流代理已存在");
            releaseUnregisteredPuller(proxyPlayer, failure);
            throw failure;
        }
        proxyResultCallbacks.put(key, resultCallback);
        proxyPlayerCallbacks.put(key, closeCallback);
        try {
            api().mk_proxy_player_set_on_play_result(
                    proxyPlayer, resultCallback, proxyPlayer.getPointer(), null);
            api().mk_proxy_player_set_on_close(proxyPlayer, closeCallback, proxyPlayer.getPointer());
        } catch (RuntimeException | Error exception) {
            releasePullerAfterFailure(key, proxyPlayer, exception);
            throw exception;
        }
    }

    /**
     * 写入拉流代理的媒体输出配置。
     *
     * @param option native 配置对象
     * @param param 拉流参数
     */
    public void configurePullerOptions(MK_INI option, StreamProxyPullerBO param) {
        setPullerOption(option, "enable_mp4", param.getEnableMp4(), defaultProperties.getEnableMp4());
        setPullerOption(option, "enable_audio", param.getEnableAudio(), defaultProperties.getEnableAudio());
        setPullerOption(option, "enable_fmp4", param.getEnableFmp4(), defaultProperties.getEnableFmp4());
        setPullerOption(option, "enable_ts", param.getEnableTs(), defaultProperties.getEnableTs());
        setPullerOption(option, "enable_hls", param.getEnableHls(), defaultProperties.getEnableHls());
        setPullerOption(option, "enable_rtsp", param.getEnableRtsp(), defaultProperties.getEnableRtsp());
        setPullerOption(option, "enable_rtmp", param.getEnableRtmp(), defaultProperties.getEnableRtmp());
        setPullerOption(option, "mp4_max_second", param.getMp4MaxSecond(),
                defaultProperties.getMp4MaxSecond());
        setPullerOption(option, "add_mute_audio", 0, 0);
        setPullerOption(option, "auto_close", param.getAutoClose(), defaultProperties.getAutoClose());
        setPullerOption(option, "record.fileRepeat", param.getRecordFileRepeat(),
                defaultProperties.getRecordFileRepeat());
    }

    /**
     * 配置拉流传输超时、RTP 类型和 RTSP 倍速。
     *
     * @param proxyPlayer native 拉流代理
     * @param param 拉流参数
     * @param sourceUri 已校验的源地址
     */
    public void configurePullerTransport(MK_PROXY_PLAYER proxyPlayer, StreamProxyPullerBO param,
                                         URI sourceUri) {
        if (param.getTimeoutSec() != null) {
            api().mk_proxy_player_set_option(proxyPlayer, "protocol_timeout_ms",
                    String.valueOf(param.getTimeoutSec() * 1000));
        }
        if (sourceUri.getScheme().startsWith("rtsp") && param.getRtpType() != null) {
            api().mk_proxy_player_set_option(proxyPlayer, "rtp_type", param.getRtpType().toString());
        }
        if (param.getRtspSpeed() != null) {
            api().mk_proxy_player_set_option(proxyPlayer, "rtsp_speed", param.getRtspSpeed()
                    .setScale(2, RoundingMode.HALF_UP).toString());
        }
    }

    /**
     * 等待拉流首次连接结果。
     *
     * @param key 代理键
     * @param proxyPlayer native 拉流代理
     * @param resultQueue 首次连接结果队列
     * @return 代理键
     */
    public String awaitPullerStartup(String key, MK_PROXY_PLAYER proxyPlayer,
                                     BlockingQueue<PullerStartupResult> resultQueue) {
        PullerStartupResult result;
        try {
            result = resultQueue.poll(proxyStartupTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ZlmOperationException("等待拉流代理结果时线程被中断", exception);
        }
        if (result == null) {
            throw new ZlmOperationException("等待拉流代理结果超时");
        }
        if (!result.success()) {
            throw new ZlmOperationException("拉流代理连接失败");
        }
        if (proxyPlayers.get(key) != proxyPlayer) {
            throw new ZlmOperationException("拉流代理在启动期间已关闭");
        }
        return key;
    }

    /**
     * 幂等移除并释放指定拉流代理。
     *
     * @param key 代理键
     * @param proxyPlayer 预期释放的 native 拉流代理
     * @return 当前调用实际释放资源时返回 true
     */
    public synchronized boolean releasePuller(String key, MK_PROXY_PLAYER proxyPlayer) {
        if (proxyPlayers.get(key) != proxyPlayer || !releasingPullers.add(proxyPlayer)) {
            return false;
        }
        try {
            api().mk_proxy_player_release(proxyPlayer);
            proxyPlayers.remove(key, proxyPlayer);
            proxyPlayerCallbacks.remove(key);
            proxyResultCallbacks.remove(key);
            return true;
        } finally {
            releasingPullers.remove(proxyPlayer);
        }
    }

    /**
     * 在保留原始异常的前提下回收拉流代理。
     *
     * @param key 代理键
     * @param proxyPlayer native 拉流代理
     * @param failure 原始失败
     */
    public void releasePullerAfterFailure(String key, MK_PROXY_PLAYER proxyPlayer, Throwable failure) {
        try {
            releasePuller(key, proxyPlayer);
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * 写入整数代理配置，调用参数为空时使用实例默认值。
     *
     * @param option native 配置对象
     * @param key 配置键
     * @param value 调用参数值
     * @param defaultValue 实例默认值
     */
    public void setPullerOption(MK_INI option, String key, Integer value, Integer defaultValue) {
        api().mk_ini_set_option_int(option, key, value == null ? defaultValue : value);
    }

    /**
     * 释放拉流配置；配置释放失败时同步回收尚未注册的代理。
     *
     * @param option native 配置对象
     * @param proxyPlayer 已创建但尚未注册的拉流代理
     * @param creationFailure 创建阶段已经发生的异常
     */
    public void releasePullerOption(MK_INI option, MK_PROXY_PLAYER proxyPlayer,
                                    Throwable creationFailure) {
        try {
            api().mk_ini_release(option);
        } catch (RuntimeException | Error optionFailure) {
            if (creationFailure != null) {
                creationFailure.addSuppressed(optionFailure);
                return;
            }
            if (proxyPlayer != null) {
                releaseUnregisteredPuller(proxyPlayer, optionFailure);
            }
            throw optionFailure;
        }
    }

    /**
     * 释放尚未注册的拉流代理，失败时保留句柄并附加到原始异常。
     *
     * @param proxyPlayer native 拉流代理
     * @param failure 原始失败
     */
    public synchronized void releaseUnregisteredPuller(MK_PROXY_PLAYER proxyPlayer, Throwable failure) {
        try {
            api().mk_proxy_player_release(proxyPlayer);
        } catch (RuntimeException | Error cleanupFailure) {
            pendingPullerReleases.add(proxyPlayer);
            failure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public synchronized Boolean delStreamPullerProxy(String key) {
        MK_PROXY_PLAYER proxyPlayer = proxyPlayers.get(key);
        return proxyPlayer != null && releasePuller(key, proxyPlayer);
    }

    /**
     * 拉流代理首次连接的内部结果。
     *
     * @param success 是否连接成功
     */
    public record PullerStartupResult(boolean success) {

        /**
         * 创建成功结果。
         *
         * @return 成功结果
         */
        public static PullerStartupResult connected() {
            return new PullerStartupResult(true);
        }

        /**
         * 创建失败结果。
         *
         * @return 失败结果
         */
        public static PullerStartupResult failed() {
            return new PullerStartupResult(false);
        }
    }

    @Override
    public synchronized String addStreamPusherProxy(StreamProxyPusherBO param) {
        requireOpen();
        URI targetUri = mediaResourcePolicy.requireAllowed(param.getUrl(), MediaResourceUsage.PUSH);
        String key = getStreamKey("push", param.getApp(), param.getStream());
        MK_PUSHER pusher = createPusher(param, targetUri);
        BlockingQueue<PusherStartupResult> resultQueue = new ArrayBlockingQueue<>(1);
        registerPusherCallbacks(key, pusher, resultQueue);
        try {
            api().mk_pusher_publish(pusher, targetUri.toASCIIString());
            return awaitPusherStartup(key, pusher, resultQueue);
        } catch (RuntimeException | Error exception) {
            releasePusherAfterFailure(key, pusher, exception);
            throw exception;
        }
    }

    /**
     * 创建并配置 native 推流代理。
     *
     * @param param 推流参数
     * @param targetUri 已校验的目标地址
     * @return native 推流代理
     */
    public MK_PUSHER createPusher(StreamProxyPusherBO param, URI targetUri) {
        MK_MEDIA_SOURCE source = api().mk_media_source_find2(param.getSchema(),
                ZlmMediaServerConstants.DEFAULT_VHOST, param.getApp(), param.getStream(), 0);
        if (source == null) {
            throw new ZlmOperationException(MessageFormatter.basicArrayFormat(
                    "app:{},stream:{},scheme:{}对应的流不存在",
                    new Object[]{param.getApp(), param.getStream(), param.getSchema()}));
        }
        MK_PUSHER pusher = api().mk_pusher_create_src(source);
        if (pusher == null) {
            throw new ZlmOperationException("创建推流代理失败");
        }
        try {
            if (targetUri.getScheme().startsWith("rtsp") && param.getRtpType() != null) {
                api().mk_pusher_set_option(pusher, "rtp_type", param.getRtpType().toString());
            }
            if (param.getTimeoutSec() != null) {
                api().mk_pusher_set_option(pusher, "protocol_timeout_ms",
                        String.valueOf(param.getTimeoutSec() * 1000));
            }
            return pusher;
        } catch (RuntimeException | Error exception) {
            releaseUnregisteredPusher(pusher, exception);
            throw exception;
        }
    }

    /**
     * 注册推流首次结果和关闭回调。
     *
     * @param key 代理键
     * @param pusher native 推流代理
     * @param resultQueue 首次连接结果队列
     */
    public synchronized void registerPusherCallbacks(String key, MK_PUSHER pusher,
                                                     BlockingQueue<PusherStartupResult> resultQueue) {
        IMKPushEventCallBack resultCallback = (userData, errCode, errMsg) -> {
            resultQueue.offer(errCode == 0 ? PusherStartupResult.connected()
                    : PusherStartupResult.failed());
        };
        IMKPushEventCallBack closeCallback = (userData, errCode, errMsg) -> {
            if (releasePusher(key, pusher)) {
                log.info("【SimpleSecretZLMediaKit】推流代理关闭");
            }
        };
        MK_PUSHER existingPusher = pushers.putIfAbsent(key, pusher);
        if (existingPusher != null) {
            ZlmOperationException failure = new ZlmOperationException("推流代理已存在");
            releaseUnregisteredPusher(pusher, failure);
            throw failure;
        }
        pusherResultCallbacks.put(key, resultCallback);
        pusherCallbacks.put(key, closeCallback);
        try {
            api().mk_pusher_set_on_result(pusher, resultCallback, pusher.getPointer());
            api().mk_pusher_set_on_shutdown(pusher, closeCallback, pusher.getPointer());
        } catch (RuntimeException | Error exception) {
            releasePusherAfterFailure(key, pusher, exception);
            throw exception;
        }
    }

    /**
     * 等待推流首次连接结果。
     *
     * @param key 代理键
     * @param pusher native 推流代理
     * @param resultQueue 首次连接结果队列
     * @return 代理键
     */
    public String awaitPusherStartup(String key, MK_PUSHER pusher,
                                     BlockingQueue<PusherStartupResult> resultQueue) {
        PusherStartupResult result;
        try {
            result = resultQueue.poll(proxyStartupTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ZlmOperationException("等待推流代理结果时线程被中断", exception);
        }
        if (result == null) {
            throw new ZlmOperationException("等待推流代理结果超时");
        }
        if (!result.success()) {
            throw new ZlmOperationException("推流代理连接失败");
        }
        if (pushers.get(key) != pusher) {
            throw new ZlmOperationException("推流代理在启动期间已关闭");
        }
        return key;
    }

    /**
     * 幂等移除并释放指定推流代理。
     *
     * @param key 代理键
     * @param pusher 预期释放的 native 推流代理
     * @return 当前调用实际释放资源时返回 true
     */
    public synchronized boolean releasePusher(String key, MK_PUSHER pusher) {
        if (pushers.get(key) != pusher || !releasingPushers.add(pusher)) {
            return false;
        }
        try {
            api().mk_pusher_release(pusher);
            pushers.remove(key, pusher);
            pusherCallbacks.remove(key);
            pusherResultCallbacks.remove(key);
            return true;
        } finally {
            releasingPushers.remove(pusher);
        }
    }

    /**
     * 在保留原始异常的前提下回收推流代理。
     *
     * @param key 代理键
     * @param pusher native 推流代理
     * @param failure 原始失败
     */
    public void releasePusherAfterFailure(String key, MK_PUSHER pusher, Throwable failure) {
        try {
            releasePusher(key, pusher);
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * 释放尚未注册的推流代理，失败时保留句柄并附加到原始异常。
     *
     * @param pusher native 推流代理
     * @param failure 原始失败
     */
    public synchronized void releaseUnregisteredPusher(MK_PUSHER pusher, Throwable failure) {
        try {
            api().mk_pusher_release(pusher);
        } catch (RuntimeException | Error cleanupFailure) {
            pendingPusherReleases.add(pusher);
            failure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public synchronized Boolean delStreamPusherProxy(String key) {
        MK_PUSHER pusher = pushers.get(key);
        return pusher != null && releasePusher(key, pusher);
    }

    /**
     * 推流代理首次连接的内部结果。
     *
     * @param success 是否连接成功
     */
    public record PusherStartupResult(boolean success) {

        /**
         * 创建成功结果。
         *
         * @return 成功结果
         */
        public static PusherStartupResult connected() {
            return new PusherStartupResult(true);
        }

        /**
         * 创建失败结果。
         *
         * @return 失败结果
         */
        public static PusherStartupResult failed() {
            return new PusherStartupResult(false);
        }
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
    public synchronized Integer openRtpServer(OpenRtpServerBO param) {
        requireOpen();
        MK_RTP_SERVER mkRtpServer = api().mk_rtp_server_create(param.getPort().shortValue(), param.getTcpMode(), param.getStream());
        if (mkRtpServer == null) {
            return -1;
        }
        short port;
        try {
            port = api().mk_rtp_server_port(mkRtpServer);
        } catch (RuntimeException | Error exception) {
            releaseUnregisteredRtpServer(mkRtpServer, exception);
            throw exception;
        }
        IMKRtpServerDetachCallBack detachCallback = userData ->
                releaseRtpServer(param.getStream(), mkRtpServer);
        MK_RTP_SERVER existingServer = rtpServers.putIfAbsent(param.getStream(), mkRtpServer);
        if (existingServer != null) {
            ZlmOperationException failure = new ZlmOperationException("RTP 服务已存在");
            releaseUnregisteredRtpServer(mkRtpServer, failure);
            if (failure.getSuppressed().length > 0) {
                throw failure;
            }
            return -1;
        }
        rtpDetachCallbacks.put(param.getStream(), detachCallback);
        try {
            api().mk_rtp_server_set_on_detach(mkRtpServer, detachCallback, null);
            return (int) port;
        } catch (RuntimeException | Error exception) {
            releaseRtpAfterFailure(param.getStream(), mkRtpServer, exception);
            throw exception;
        }
    }

    @Override
    public synchronized Boolean closeRtpServer(String streamId) {
        MK_RTP_SERVER rtpServer = rtpServers.get(streamId);
        return rtpServer != null && releaseRtpServer(streamId, rtpServer);
    }

    /**
     * 幂等释放指定 RTP 服务。
     *
     * @param streamId 流标识
     * @param rtpServer 预期释放的 native RTP 服务
     * @return 当前调用实际释放资源时返回 true
     */
    public synchronized boolean releaseRtpServer(String streamId, MK_RTP_SERVER rtpServer) {
        if (rtpServers.get(streamId) != rtpServer || !releasingRtpServers.add(rtpServer)) {
            return false;
        }
        try {
            api().mk_rtp_server_release(rtpServer);
            rtpServers.remove(streamId, rtpServer);
            rtpDetachCallbacks.remove(streamId);
            return true;
        } finally {
            releasingRtpServers.remove(rtpServer);
        }
    }

    /**
     * 在保留原始异常的前提下回收 RTP 服务。
     *
     * @param streamId 流标识
     * @param rtpServer native RTP 服务
     * @param failure 原始失败
     */
    public void releaseRtpAfterFailure(String streamId, MK_RTP_SERVER rtpServer, Throwable failure) {
        try {
            releaseRtpServer(streamId, rtpServer);
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * 释放尚未注册的 RTP 服务，失败时保留句柄并附加到原始异常。
     *
     * @param rtpServer native RTP 服务
     * @param failure 原始失败
     */
    public synchronized void releaseUnregisteredRtpServer(MK_RTP_SERVER rtpServer, Throwable failure) {
        try {
            api().mk_rtp_server_release(rtpServer);
        } catch (RuntimeException | Error cleanupFailure) {
            pendingRtpReleases.add(rtpServer);
            failure.addSuppressed(cleanupFailure);
        }
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
    public synchronized void close() {
        closed = true;
        Throwable failure = releaseAllPullers(null);
        failure = releaseAllPushers(failure);
        failure = releaseAllRtpServers(failure);
        failure = releasePendingPullers(failure);
        failure = releasePendingPushers(failure);
        failure = releasePendingRtpServers(failure);
        throwCleanupFailure(failure);
    }

    /**
     * 尝试释放全部拉流代理并聚合失败。
     *
     * @param failure 已记录的首个失败
     * @return 聚合后的失败，没有失败时返回 null
     */
    public Throwable releaseAllPullers(Throwable failure) {
        for (Map.Entry<String, MK_PROXY_PLAYER> entry : Map.copyOf(proxyPlayers).entrySet()) {
            try {
                releasePuller(entry.getKey(), entry.getValue());
            } catch (RuntimeException | Error exception) {
                failure = appendCleanupFailure(failure, exception);
            }
        }
        return failure;
    }

    /**
     * 尝试释放全部推流代理并聚合失败。
     *
     * @param failure 已记录的首个失败
     * @return 聚合后的失败，没有失败时返回 null
     */
    public Throwable releaseAllPushers(Throwable failure) {
        for (Map.Entry<String, MK_PUSHER> entry : Map.copyOf(pushers).entrySet()) {
            try {
                releasePusher(entry.getKey(), entry.getValue());
            } catch (RuntimeException | Error exception) {
                failure = appendCleanupFailure(failure, exception);
            }
        }
        return failure;
    }

    /**
     * 尝试释放全部 RTP 服务并聚合失败。
     *
     * @param failure 已记录的首个失败
     * @return 聚合后的失败，没有失败时返回 null
     */
    public Throwable releaseAllRtpServers(Throwable failure) {
        for (Map.Entry<String, MK_RTP_SERVER> entry : Map.copyOf(rtpServers).entrySet()) {
            try {
                releaseRtpServer(entry.getKey(), entry.getValue());
            } catch (RuntimeException | Error exception) {
                failure = appendCleanupFailure(failure, exception);
            }
        }
        return failure;
    }

    /**
     * 重试释放尚未注册的拉流代理。
     *
     * @param failure 已记录的首个失败
     * @return 聚合后的失败，没有失败时返回 null
     */
    public Throwable releasePendingPullers(Throwable failure) {
        for (MK_PROXY_PLAYER proxyPlayer : Set.copyOf(pendingPullerReleases)) {
            try {
                api().mk_proxy_player_release(proxyPlayer);
                pendingPullerReleases.remove(proxyPlayer);
            } catch (RuntimeException | Error exception) {
                failure = appendCleanupFailure(failure, exception);
            }
        }
        return failure;
    }

    /**
     * 重试释放尚未注册的推流代理。
     *
     * @param failure 已记录的首个失败
     * @return 聚合后的失败，没有失败时返回 null
     */
    public Throwable releasePendingPushers(Throwable failure) {
        for (MK_PUSHER pusher : Set.copyOf(pendingPusherReleases)) {
            try {
                api().mk_pusher_release(pusher);
                pendingPusherReleases.remove(pusher);
            } catch (RuntimeException | Error exception) {
                failure = appendCleanupFailure(failure, exception);
            }
        }
        return failure;
    }

    /**
     * 重试释放尚未注册的 RTP 服务。
     *
     * @param failure 已记录的首个失败
     * @return 聚合后的失败，没有失败时返回 null
     */
    public Throwable releasePendingRtpServers(Throwable failure) {
        for (MK_RTP_SERVER rtpServer : Set.copyOf(pendingRtpReleases)) {
            try {
                api().mk_rtp_server_release(rtpServer);
                pendingRtpReleases.remove(rtpServer);
            } catch (RuntimeException | Error exception) {
                failure = appendCleanupFailure(failure, exception);
            }
        }
        return failure;
    }

    /**
     * 将清理失败追加到首个异常。
     *
     * @param failure 已记录的首个失败
     * @param nextFailure 当前清理失败
     * @return 聚合后的失败
     */
    public Throwable appendCleanupFailure(Throwable failure, Throwable nextFailure) {
        if (failure == null) {
            return nextFailure;
        }
        failure.addSuppressed(nextFailure);
        return failure;
    }

    /**
     * 在全部资源都尝试清理后重新抛出首个失败。
     *
     * @param failure 聚合后的清理失败
     */
    public void throwCleanupFailure(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    /** 拒绝在服务关闭后创建新的 native 资源。 */
    public synchronized void requireOpen() {
        if (closed) {
            throw new ZlmOperationException("ZLMediaKit 媒体服务已关闭");
        }
    }

    private ZLMApi api() {
        return zlmApi != null ? zlmApi : ZlmMediaHelper.getZlmApi();
    }


}

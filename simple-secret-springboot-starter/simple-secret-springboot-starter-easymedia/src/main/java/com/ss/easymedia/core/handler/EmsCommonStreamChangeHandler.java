package com.ss.easymedia.core.handler;

import com.aizuda.zlm4j.callback.IMKFrameOutCallBack;
import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.aizuda.zlm4j.structure.MK_TRACK;
import com.ss.easymedia.callback.TrackDelegateCallback;
import com.ss.easymedia.config.properties.EmsProperties;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.domain.TrackDomain;
import com.ss.zlm4j.handler.StreamChangeHandler;
import com.ss.zlm4j.helper.ZlmMediaHelper;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ems通用流改变处理器
 *
 * @author JunPzx
 * @since 2025/12/18 15:38
 */
public class EmsCommonStreamChangeHandler implements StreamChangeHandler {

    private static final Logger log = LoggerFactory.getLogger(EmsCommonStreamChangeHandler.class);

    private final List<TrackDelegateCallback> trackDelegateCallbacks;

    /** EasyMedia 资源边界配置。 */
    private final EmsProperties properties;

    /** 按原生媒体源指针关联的注册 token 与轨道去重状态。 */
    private final Map<Long, RegisteredLifecycle> registeredLifecycles = new ConcurrentHashMap<>();


    /**
     * 创建并初始化实例。
     *
     * @param trackDelegateCallbacks 媒体轨道回调列表
     */
    public EmsCommonStreamChangeHandler(List<TrackDelegateCallback> trackDelegateCallbacks) {
        this(trackDelegateCallbacks, new EmsProperties());
    }

    /**
     * 创建并初始化带资源边界的实例。
     *
     * @param trackDelegateCallbacks 媒体轨道回调列表
     * @param properties EasyMedia 资源边界配置
     */
    public EmsCommonStreamChangeHandler(List<TrackDelegateCallback> trackDelegateCallbacks,
                                        EmsProperties properties) {
        this.trackDelegateCallbacks = trackDelegateCallbacks == null ? List.of() : List.copyOf(trackDelegateCallbacks);
        this.properties = Objects.requireNonNull(properties, "properties");
    }


    @Override
    public void handleRegister(MK_MEDIA_SOURCE sender) {
        MediaSourceDomain mediaSource = ZlmMediaHelper.Assembler.getMediaSource(sender, true);
        List<TrackDelegateCallback> callbacks = resolveCallbacks(mediaSource.getSchema());
        if (callbacks.isEmpty()) {
            return;
        }
        RegisteredLifecycle lifecycle = rememberRegisteredLifecycle(sender, mediaSource);
        Set<String> codecSet = lifecycle.codecSet;
        notifyRegistered(mediaSource, callbacks);
        List<TrackDomain> tracks = mediaSource.getTracks();
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        for (int i = 0, tracksSize = tracks.size(); i < tracksSize; i++) {
            TrackDomain track = tracks.get(i);
            if (codecSet.contains(track.getCodecIdName())) {
                continue;
            }
            MK_TRACK mkTrack = ZlmMediaHelper.getZlmApi().mk_media_source_get_track(sender, i);
            // 轨道添加监听
            IMKFrameOutCallBack delegate = (userData, frame) -> {
                // 获取帧数据
                long dataSize = ZlmMediaHelper.getZlmApi().mk_frame_get_data_size(frame);
                long dts = ZlmMediaHelper.getZlmApi().mk_frame_get_dts(frame);
                long pts = ZlmMediaHelper.getZlmApi().mk_frame_get_pts(frame);
                long flag = ZlmMediaHelper.getZlmApi().mk_frame_get_flags(frame);
                long dataPrefixSize = ZlmMediaHelper.getZlmApi().mk_frame_get_data_prefix_size(frame);
                Pointer pointer = ZlmMediaHelper.getZlmApi().mk_frame_get_data(frame);
                acceptNativeFrame(mediaSource, track, dataSize, dts, pts, flag, dataPrefixSize, pointer, callbacks);
            };
            installTrackDelegate(lifecycle, mkTrack, delegate,
                    () -> ZlmMediaHelper.getZlmApi().mk_track_add_delegate(mkTrack, delegate, Pointer.NULL));
            codecSet.add(track.getCodecIdName());
        }
    }

    @Override
    public void handleDeregister(MK_MEDIA_SOURCE sender) {
        MediaSourceDomain fallback = ZlmMediaHelper.Assembler.getMediaSource(sender, false);
        MediaSourceDomain lifecycle = resolveDeregisteredLifecycle(sender, fallback);
        notifyDeregistered(lifecycle, resolveCallbacks(lifecycle.getSchema()));
    }

    /**
     * 关联原生媒体源身份和注册时创建的媒体源 token。
     *
     * @param sender ZLMediaKit 原生媒体源
     * @param mediaSource 注册时创建的媒体源 token
     * @return 当前原生媒体源的精确注册生命周期
     */
    RegisteredLifecycle rememberRegisteredLifecycle(MK_MEDIA_SOURCE sender, MediaSourceDomain mediaSource) {
        RegisteredLifecycle lifecycle = new RegisteredLifecycle(mediaSource);
        registeredLifecycles.put(nativeSourceIdentity(sender), lifecycle);
        return lifecycle;
    }

    /**
     * 获取并清理指定原生媒体源精确对应的注册 token。
     *
     * @param sender ZLMediaKit 原生媒体源
     * @param fallback 无注册记录时使用的注销媒体源信息
     * @return 注册时的精确媒体源 token，或回退信息
     */
    MediaSourceDomain resolveDeregisteredLifecycle(MK_MEDIA_SOURCE sender, MediaSourceDomain fallback) {
        long sourceIdentity = nativeSourceIdentity(sender);
        RegisteredLifecycle lifecycle = registeredLifecycles.remove(sourceIdentity);
        return lifecycle == null ? fallback : lifecycle.mediaSource;
    }

    /** @return 当前保留的原生媒体源生命周期 token 数 */
    int registeredLifecycleCount() {
        return registeredLifecycles.size();
    }

    /**
     * 在指定注册生命周期中保留原生轨道和 JNA 回调强引用。
     *
     * @param sender ZLMediaKit 原生媒体源
     * @param track 已安装代理的原生轨道
     * @param delegate JNA 帧回调
     */
    void retainTrackDelegate(MK_MEDIA_SOURCE sender, MK_TRACK track, IMKFrameOutCallBack delegate) {
        RegisteredLifecycle lifecycle = registeredLifecycles.get(nativeSourceIdentity(sender));
        if (lifecycle != null) {
            lifecycle.retain(track, delegate);
        }
    }

    /**
     * 保留轨道代理并执行原生安装，安装失败时撤销强引用。
     *
     * @param lifecycle 原生媒体源注册生命周期
     * @param track 待安装代理的原生轨道
     * @param delegate JNA 帧回调
     * @param nativeInstall 原生安装动作
     */
    void installTrackDelegate(RegisteredLifecycle lifecycle, MK_TRACK track, IMKFrameOutCallBack delegate,
                              Runnable nativeInstall) {
        lifecycle.retain(track, delegate);
        try {
            nativeInstall.run();
        } catch (RuntimeException | Error exception) {
            lifecycle.release(track, delegate);
            throw exception;
        }
    }

    /**
     * 返回指定原生媒体源当前保留的轨道代理数量。
     *
     * @param sender ZLMediaKit 原生媒体源
     * @return 指定原生媒体源当前保留的轨道代理数量
     */
    int retainedTrackDelegateCount(MK_MEDIA_SOURCE sender) {
        RegisteredLifecycle lifecycle = registeredLifecycles.get(nativeSourceIdentity(sender));
        return lifecycle == null ? 0 : lifecycle.retainedTrackDelegates.size();
    }

    /**
     * 返回测试可观察的保留轨道。
     *
     * @param sender ZLMediaKit 原生媒体源
     * @param index 轨道索引
     * @return 被生命周期保留的原生轨道
     */
    MK_TRACK retainedTrack(MK_MEDIA_SOURCE sender, int index) {
        return registeredLifecycles.get(nativeSourceIdentity(sender)).retainedTrackDelegates.get(index).track;
    }

    /**
     * 返回测试可观察的保留回调。
     *
     * @param sender ZLMediaKit 原生媒体源
     * @param index 轨道索引
     * @return 被生命周期保留的 JNA 回调
     */
    IMKFrameOutCallBack retainedTrackDelegate(MK_MEDIA_SOURCE sender, int index) {
        return registeredLifecycles.get(nativeSourceIdentity(sender)).retainedTrackDelegates.get(index).delegate;
    }

    /**
     * 获取一个原生媒体源在本进程生命周期中的指针身份。
     *
     * @param sender ZLMediaKit 原生媒体源
     * @return 原生指针地址
     */
    private long nativeSourceIdentity(MK_MEDIA_SOURCE sender) {
        return Pointer.nativeValue(sender.getPointer());
    }

    /**
     * 单个原生媒体源的注册 token 和轨道去重状态。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    static final class RegisteredLifecycle {

        /** 注册时创建并由帧回调共享的媒体源 token。 */
        private final MediaSourceDomain mediaSource;

        /** 当前原生媒体源已经安装代理的轨道编码。 */
        private final Set<String> codecSet = ConcurrentHashMap.newKeySet();

        /** 已安装轨道及其 JNA 回调的强引用。 */
        private final List<RetainedTrackDelegate> retainedTrackDelegates = new CopyOnWriteArrayList<>();

        /**
         * 创建原生媒体源注册状态。
         *
         * @param mediaSource 注册时创建的媒体源 token
         */
        private RegisteredLifecycle(MediaSourceDomain mediaSource) {
            this.mediaSource = mediaSource;
        }

        /**
         * 保留原生轨道和回调，防止 JNA 回调被垃圾回收。
         * <p>
         * zlm4j 1.11.0 的 add 绑定返回 void，无法取得 del 所需 tag。
         * 因此这里只管理 Java 引用生命周期。
         *
         * @param track 原生轨道
         * @param delegate JNA 帧回调
         */
        private void retain(MK_TRACK track, IMKFrameOutCallBack delegate) {
            retainedTrackDelegates.add(new RetainedTrackDelegate(track, delegate));
        }

        /**
         * 原生代理安装失败时撤销对应的 Java 强引用。
         *
         * @param track 原生轨道
         * @param delegate JNA 帧回调
         */
        private void release(MK_TRACK track, IMKFrameOutCallBack delegate) {
            retainedTrackDelegates.removeIf(retained -> retained.matches(track, delegate));
        }
    }

    /**
     * 单条已安装原生轨道代理的强引用所有者。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    private static final class RetainedTrackDelegate {

        /** 已安装代理的原生轨道。 */
        private final MK_TRACK track;

        /** 必须与原生代理保持相同生命周期的 JNA 回调。 */
        private final IMKFrameOutCallBack delegate;

        /**
         * 创建轨道代理强引用。
         *
         * @param track 原生轨道
         * @param delegate JNA 帧回调
         */
        private RetainedTrackDelegate(MK_TRACK track, IMKFrameOutCallBack delegate) {
            this.track = track;
            this.delegate = delegate;
        }

        /**
         * 判断是否为同一原生轨道代理。
         *
         * @param candidateTrack 待比较轨道
         * @param candidateDelegate 待比较回调
         * @return 轨道和回调均相同时返回 true
         */
        private boolean matches(MK_TRACK candidateTrack, IMKFrameOutCallBack candidateDelegate) {
            return track == candidateTrack && delegate == candidateDelegate;
        }
    }

    /**
     * 解析支持指定媒体源协议的轨道回调。
     *
     * @param schema 媒体源协议
     * @return 匹配的轨道回调
     */
    List<TrackDelegateCallback> resolveCallbacks(String schema) {
        if (schema == null || schema.isBlank()) {
            return List.of();
        }
        return trackDelegateCallbacks.stream()
                .filter(callback -> supportsSchema(callback, schema))
                .toList();
    }

    /**
     * 判断轨道回调是否支持指定协议。
     *
     * @param callback 轨道回调
     * @param schema 媒体源协议
     * @return 是否支持
     */
    private boolean supportsSchema(TrackDelegateCallback callback, String schema) {
        try {
            Set<String> schemas = callback.supportedSchemas();
            return schemas != null && schemas.stream()
                    .filter(Objects::nonNull)
                    .filter(candidate -> !candidate.isBlank())
                    .anyMatch(candidate -> candidate.equalsIgnoreCase(schema));
        } catch (RuntimeException e) {
            log.error("读取轨道回调支持协议失败，callback:{}, schema:{}",
                    callback.getClass().getName(), schema, e);
            return false;
        }
    }

    /**
     * 通知匹配回调媒体源已注册。
     *
     * @param mediaSource 媒体源信息
     * @param callbacks 匹配的轨道回调
     */
    void notifyRegistered(MediaSourceDomain mediaSource, List<TrackDelegateCallback> callbacks) {
        callbacks.forEach(callback -> invokeSafely(callback, mediaSource,
                () -> callback.onMediaSourceRegistered(mediaSource), "register"));
    }

    /**
     * 通知匹配回调媒体源已注销。
     *
     * @param mediaSource 媒体源信息
     * @param callbacks 匹配的轨道回调
     */
    void notifyDeregistered(MediaSourceDomain mediaSource, List<TrackDelegateCallback> callbacks) {
        callbacks.forEach(callback -> invokeSafely(callback, mediaSource,
                () -> callback.onMediaSourceDeregistered(mediaSource), "deregister"));
    }

    /**
     * 将媒体轨道帧分发给匹配回调。
     *
     * @param mediaSource 媒体源信息
     * @param track 轨道信息
     * @param delegateInfo 帧信息
     * @param callbacks 匹配的轨道回调
     */
    void dispatchFrame(MediaSourceDomain mediaSource, TrackDomain track,
                       TrackDelegateCallback.TackDelegateInfo delegateInfo,
                       List<TrackDelegateCallback> callbacks) {
        callbacks.forEach(callback -> invokeSafely(callback, mediaSource,
                () -> callback.callback(mediaSource, track, delegateInfo), "frame"));
    }

    /**
     * 校验并复制原生帧，然后同步分发给业务回调。
     *
     * @param mediaSource 媒体源信息
     * @param track 轨道信息
     * @param dataSize 原生帧字节数
     * @param dts 解码时间戳
     * @param pts 显示时间戳
     * @param flag 原生帧标志
     * @param dataPrefixSize 数据前缀字节数
     * @param pointer 原生帧数据指针
     * @param callbacks 匹配的业务回调
     * @return 帧完成复制并分发时返回 true
     */
    boolean acceptNativeFrame(MediaSourceDomain mediaSource, TrackDomain track, long dataSize, long dts, long pts,
                              long flag, long dataPrefixSize, Pointer pointer,
                              List<TrackDelegateCallback> callbacks) {
        String rejectionReason = nativeFrameRejectionReason(dataSize, pointer);
        if (rejectionReason != null) {
            log.warn("Reject native track frame: app={}, stream={}, codec={}, size={}, reason={}",
                    mediaSource.getApp(), mediaSource.getStream(), track.getCodecIdName(), dataSize, rejectionReason);
            return false;
        }
        int safeDataSize = Math.toIntExact(dataSize);
        byte[] data = new byte[safeDataSize];
        pointer.read(0, data, 0, safeDataSize);
        TrackDelegateCallback.TackDelegateInfo delegateInfo = new TrackDelegateCallback.TackDelegateInfo()
                .setDataLength(dataSize)
                .setData(data)
                .setPts(pts)
                .setDts(dts)
                .setFlag(flag)
                .setDataPrefixSize(dataPrefixSize);
        dispatchFrame(mediaSource, track, delegateInfo, callbacks);
        return true;
    }

    /**
     * 返回原生帧拒绝原因。
     *
     * @param dataSize 原生帧字节数
     * @param pointer 原生数据指针
     * @return 合法时为 null，否则为稳定拒绝原因
     */
    private String nativeFrameRejectionReason(long dataSize, Pointer pointer) {
        if (dataSize <= 0L) {
            return "non-positive-size";
        }
        if (dataSize > Integer.MAX_VALUE) {
            return "integer-overflow";
        }
        if (dataSize > properties.getMaxTrackFrameBytes()) {
            return "configured-limit";
        }
        if (pointer == null || Pointer.nativeValue(pointer) == 0L) {
            return "null-data-pointer";
        }
        return null;
    }

    /**
     * 隔离执行单个业务回调。
     *
     * @param callback 轨道回调
     * @param mediaSource 媒体源信息
     * @param action 回调动作
     * @param event 事件类型
     */
    private void invokeSafely(TrackDelegateCallback callback, MediaSourceDomain mediaSource,
                              Runnable action, String event) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("执行轨道业务回调失败，event:{}, callback:{}, schema:{}, app:{}, stream:{}",
                    event,
                    callback.getClass().getName(),
                    mediaSource == null ? null : mediaSource.getSchema(),
                    mediaSource == null ? null : mediaSource.getApp(),
                    mediaSource == null ? null : mediaSource.getStream(),
                    e);
        }
    }

    /**
     * 构建媒体源轨道去重键。
     *
     * @param schema 媒体源协议
     * @param app 媒体应用
     * @param stream 流标识
     * @return 轨道去重键
     */
    public String key(String schema, String app, String stream) {
        String normalizedSchema = schema == null ? "" : schema.toLowerCase(Locale.ROOT);
        return normalizedSchema + "_" + app + "_" + stream;
    }
}

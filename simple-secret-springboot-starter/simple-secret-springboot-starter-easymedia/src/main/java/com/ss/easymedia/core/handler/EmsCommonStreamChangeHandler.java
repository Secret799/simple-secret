package com.ss.easymedia.core.handler;

import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.aizuda.zlm4j.structure.MK_TRACK;
import com.ss.easymedia.callback.TrackDelegateCallback;
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

/**
 * ems通用流改变处理器
 *
 * @author JunPzx
 * @since 2025/12/18 15:38
 */
public class EmsCommonStreamChangeHandler implements StreamChangeHandler {

    private static final Logger log = LoggerFactory.getLogger(EmsCommonStreamChangeHandler.class);

    private final List<TrackDelegateCallback> trackDelegateCallbacks;

    /** 按原生媒体源指针关联的注册 token 与轨道去重状态。 */
    private final Map<Long, RegisteredLifecycle> registeredLifecycles = new ConcurrentHashMap<>();


    /**
     * 创建并初始化实例。
     *
     * @param trackDelegateCallbacks 媒体轨道回调列表
     */
    public EmsCommonStreamChangeHandler(List<TrackDelegateCallback> trackDelegateCallbacks) {
        this.trackDelegateCallbacks = trackDelegateCallbacks == null ? List.of() : List.copyOf(trackDelegateCallbacks);
    }


    @Override
    public void handleRegister(MK_MEDIA_SOURCE sender) {
        MediaSourceDomain mediaSource = ZlmMediaHelper.Assembler.getMediaSource(sender, true);
        List<TrackDelegateCallback> callbacks = resolveCallbacks(mediaSource.getSchema());
        if (callbacks.isEmpty()) {
            return;
        }
        Set<String> codecSet = rememberRegisteredLifecycle(sender, mediaSource);
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
            ZlmMediaHelper.getZlmApi().mk_track_add_delegate(mkTrack, (userData, frame) -> {
                // 获取帧数据
                long dataSize = ZlmMediaHelper.getZlmApi().mk_frame_get_data_size(frame);
                long dts = ZlmMediaHelper.getZlmApi().mk_frame_get_dts(frame);
                long pts = ZlmMediaHelper.getZlmApi().mk_frame_get_pts(frame);
                long flag = ZlmMediaHelper.getZlmApi().mk_frame_get_flags(frame);
                long dataPrefixSize = ZlmMediaHelper.getZlmApi().mk_frame_get_data_prefix_size(frame);
                Pointer pointer = ZlmMediaHelper.getZlmApi().mk_frame_get_data(frame);
                byte[] data = new byte[(int) dataSize];
                pointer.read(0, data, 0, (int) dataSize);
                // 触发回调
                TrackDelegateCallback.TackDelegateInfo delegateInfo = new TrackDelegateCallback.TackDelegateInfo()
                        .setDataLength(dataSize)
                        .setData(data)
                        .setPts(pts)
                        .setDts(dts)
                        .setFlag(flag)
                        .setDataPrefixSize(dataPrefixSize);
                dispatchFrame(mediaSource, track, delegateInfo, callbacks);
            }, Pointer.NULL);
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
     * @return 当前原生媒体源用于轨道代理去重的编码集合
     */
    Set<String> rememberRegisteredLifecycle(MK_MEDIA_SOURCE sender, MediaSourceDomain mediaSource) {
        RegisteredLifecycle lifecycle = new RegisteredLifecycle(mediaSource);
        registeredLifecycles.put(nativeSourceIdentity(sender), lifecycle);
        return lifecycle.codecSet;
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
    private static final class RegisteredLifecycle {

        /** 注册时创建并由帧回调共享的媒体源 token。 */
        private final MediaSourceDomain mediaSource;

        /** 当前原生媒体源已经安装代理的轨道编码。 */
        private final Set<String> codecSet = ConcurrentHashMap.newKeySet();

        /**
         * 创建原生媒体源注册状态。
         *
         * @param mediaSource 注册时创建的媒体源 token
         */
        private RegisteredLifecycle(MediaSourceDomain mediaSource) {
            this.mediaSource = mediaSource;
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

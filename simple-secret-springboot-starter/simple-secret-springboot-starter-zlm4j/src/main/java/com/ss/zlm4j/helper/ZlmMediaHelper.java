package com.ss.zlm4j.helper;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.*;
import com.ss.common.toolbox.function.SerializableFunction;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.context.ZlmMediaContext;
import com.ss.zlm4j.domain.*;
import com.ss.zlm4j.enums.SchemeEnum;
import com.ss.zlm4j.service.IZlmMediaService;
import com.ss.zlm4j.support.SpringUtils;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * zlm媒体服务helper
 *
 * @author JunPzx
 * @since 2024/6/18 09:25
 */
public interface ZlmMediaHelper {

    /**
     * 获取Zlm4jMediaContext
     *
     * @return Zlm4jMediaContext
     */
    static ZlmMediaContext getContext() {
        ZlmMediaContext content = SpringUtils.getBean(ZlmMediaContext.class);
        if (null == content) {
            throw new RuntimeException("SimpleSecret zlm not enabled");
        }
        return content;
    }


    /**
     * 获取Zlm4jService
     *
     * @return Zlm4jService
     */
    static IZlmMediaService getZlm4jMediaService() {
        IZlmMediaService zlm4jService = SpringUtils.getBean(IZlmMediaService.class);
        if (null == zlm4jService) {
            throw new RuntimeException("IZlm4jService未注册");
        }
        return zlm4jService;
    }

    /**
     * 获取ZLMApi
     *
     * @return ZLMApi
     */
    static ZLMApi getZlmApi() {
        return getContext().getZlmApi();
    }

    /**
     * 组装器,用于组装zlm原始结构
     */
    interface Assembler {

        /** 组装器日志。 */
        Logger LOG = LoggerFactory.getLogger(Assembler.class);

        /**
         * 根据流信息获取流媒体信息
         *
         * @param mediaInfo 流信息
         * @return 流信息
         */
        static MediaInfoDomain getMediaInfo(MK_MEDIA_INFO mediaInfo) {
            return new MediaInfoDomain()
                    .setStream(getZlmApi().mk_media_info_get_stream(mediaInfo))
                    .setApp(getZlmApi().mk_media_info_get_app(mediaInfo))
                    .setSchema(getZlmApi().mk_media_info_get_schema(mediaInfo))
                    .setVhost(getZlmApi().mk_media_info_get_vhost(mediaInfo))
                    .setPort(getZlmApi().mk_media_info_get_port(mediaInfo))
                    .setHost(getZlmApi().mk_media_info_get_host(mediaInfo))
                    .setParams(getZlmApi().mk_media_info_get_params(mediaInfo));
        }

        /**
         * 组装媒体信息
         *
         * @param ctx 媒体信息资源
         * @return 媒体信息
         */
        static MediaSourceDomain getMediaSource(MK_MEDIA_SOURCE ctx) {
            return getMediaSource(ctx, true);
        }


        /**
         * 组装媒体信息
         *
         * @param ctx                   媒体信息资源
         * @param additionalInformation 是否需要额外信息(注意,如果流已经销毁,额外信息无法获取,会导致c++内存异常,系统直接退出)
         * @return 媒体信息
         */
        static MediaSourceDomain getMediaSource(MK_MEDIA_SOURCE ctx, boolean additionalInformation) {
            return getMediaSource(getZlmApi(), ctx, additionalInformation);
        }

        /**
         * 使用指定原生 API 组装媒体信息。
         *
         * @param api                   ZLMediaKit 原生 API
         * @param ctx                   媒体信息资源
         * @param additionalInformation 是否需要额外信息
         * @return 媒体信息
         */
        public static MediaSourceDomain getMediaSource(ZLMApi api, MK_MEDIA_SOURCE ctx,
                                                       boolean additionalInformation) {
            MediaSourceDomain mediaInfoResult = new MediaSourceDomain();
            String app = api.mk_media_source_get_app(ctx);
            String stream = api.mk_media_source_get_stream(ctx);
            String schema = api.mk_media_source_get_schema(ctx);
            mediaInfoResult.setApp(app);
            mediaInfoResult.setStream(stream);
            mediaInfoResult.setSchema(schema);
            if (!additionalInformation) {
                return mediaInfoResult;
            }
            assembleAdditionalInformation(api, ctx, mediaInfoResult, app, stream);
            return mediaInfoResult;
        }

        /**
         * 组装媒体源额外信息和轨道快照。
         *
         * @param api             ZLMediaKit 原生 API
         * @param ctx             媒体信息资源
         * @param mediaSource     待填充的媒体信息
         * @param app             应用名
         * @param stream          流标识
         */
        public static void assembleAdditionalInformation(ZLMApi api, MK_MEDIA_SOURCE ctx,
                                                         MediaSourceDomain mediaSource, String app, String stream) {
            mediaSource.setReaderCount(api.mk_media_source_get_reader_count(ctx));
            mediaSource.setTotalReaderCount(api.mk_media_source_get_total_reader_count(ctx));
            mediaSource.setOriginType(api.mk_media_source_get_origin_type(ctx));
            Pointer originUrl = api.mk_media_source_get_origin_url(ctx);
            mediaSource.setOriginUrl(originUrl.getString(0));
            mediaSource.setCreateStamp(api.mk_media_source_get_create_stamp(ctx));
            int trackCount = api.mk_media_source_get_track_count(ctx);
            mediaSource.setTracks(assembleTracks(api, ctx, trackCount, app, stream));
        }

        /**
         * 复制轨道元数据，并释放每个由媒体源返回的有效复制引用。
         *
         * @param api        ZLMediaKit 原生 API
         * @param ctx        媒体信息资源
         * @param trackCount 轨道数量
         * @param app        应用名
         * @param stream     流标识
         * @return 可用轨道元数据
         */
        public static List<TrackDomain> assembleTracks(ZLMApi api, MK_MEDIA_SOURCE ctx, int trackCount,
                                                       String app, String stream) {
            List<TrackDomain> tracks = new ArrayList<>();
            for (int index = 0; index < trackCount; index++) {
                MK_TRACK track = api.mk_media_source_get_track(ctx, index);
                if (isNullTrack(track)) {
                    LOG.warn("Native media source returned null track: app={}, stream={}, trackIndex={}",
                            app, stream, index);
                    continue;
                }
                try {
                    tracks.add(assembleTrack(api, track));
                } catch (RuntimeException | Error exception) {
                    LOG.warn("Read native track metadata failed: app={}, stream={}, trackIndex={}, errorType={}",
                            app, stream, index, exception.getClass().getName());
                } finally {
                    unrefTrackSafely(api, track, app, stream, index);
                }
            }
            return tracks;
        }

        /**
         * 复制单条原生轨道的元数据。
         *
         * @param api   ZLMediaKit 原生 API
         * @param track 原生轨道引用
         * @return Java 轨道元数据
         */
        public static TrackDomain assembleTrack(ZLMApi api, MK_TRACK track) {
            int isVideo = api.mk_track_is_video(track);
            TrackDomain result = new TrackDomain();
            result.setCodecId(api.mk_track_codec_id(track));
            result.setCodecIdName(api.mk_track_codec_name(track));
            result.setBitRate(api.mk_track_bit_rate(track));
            result.setIsVideo(isVideo);
            if (isVideo == 1) {
                result.setWidth(api.mk_track_video_width(track));
                result.setHeight(api.mk_track_video_height(track));
                result.setFps(api.mk_track_video_fps(track));
                return result;
            }
            result.setSampleRate(api.mk_track_audio_sample_rate(track));
            result.setAudioChannel(api.mk_track_audio_channel(track));
            result.setAudioSampleBit(api.mk_track_audio_sample_bit(track));
            return result;
        }

        /**
         * 判断轨道是否缺少有效原生指针。
         *
         * @param track 原生轨道引用
         * @return {@code true} 表示轨道为空或原生指针为零
         */
        public static boolean isNullTrack(MK_TRACK track) {
            return track == null || track.getPointer() == null || Pointer.nativeValue(track.getPointer()) == 0L;
        }

        /**
         * 释放轨道复制引用，隔离单个原生释放失败。
         *
         * @param api    ZLMediaKit 原生 API
         * @param track  原生轨道引用
         * @param app    应用名
         * @param stream 流标识
         * @param index  轨道索引
         */
        public static void unrefTrackSafely(ZLMApi api, MK_TRACK track, String app, String stream, int index) {
            try {
                api.mk_track_unref(track);
            } catch (RuntimeException | Error exception) {
                LOG.warn("Release native track failed: app={}, stream={}, trackIndex={}, errorType={}",
                        app, stream, index, exception.getClass().getName());
            }
        }

        /**
         * 获取录制信息
         *
         * @param info 录制信息
         * @return 录制信息
         */
        static RecordInfoDomain getRecordInfo(MK_RECORD_INFO info) {
            String path = ZlmMediaHelper.getZlmApi().mk_record_info_get_file_path(info);
            String folder = ZlmMediaHelper.getZlmApi().mk_record_info_get_folder(info);
            float timeLen = ZlmMediaHelper.getZlmApi().mk_record_info_get_time_len(info);
            long fileSize = ZlmMediaHelper.getZlmApi().mk_record_info_get_file_size(info);
            // GMT 标准时间，单位秒
            LocalDateTime startTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(
                    ZlmMediaHelper.getZlmApi().mk_record_info_get_start_time(info) * 1000),
                    ZoneId.systemDefault());
            String app = ZlmMediaHelper.getZlmApi().mk_record_info_get_app(info);
            String fileName = ZlmMediaHelper.getZlmApi().mk_record_info_get_file_name(info);
            String stream = ZlmMediaHelper.getZlmApi().mk_record_info_get_stream(info);
            String url = ZlmMediaHelper.getZlmApi().mk_record_info_get_url(info);
            String vhost = ZlmMediaHelper.getZlmApi().mk_record_info_get_vhost(info);
            return new RecordInfoDomain()
                    .setDuration(timeLen)
                    .setStartTime(startTime)
                    .setFileSize(fileSize)
                    .setFilePath(path)
                    .setFileName(fileName)
                    .setUrl(url)
                    .setApp(app)
                    .setStream(stream)
                    .setVhost(vhost)
                    .setFolderPath(folder);
        }

        /**
         * 获取连接客户端信息
         *
         * @param sender 连接客户端
         * @return 连接客户端信息
         */
        static SocketInfoDomain getSocketInfo(MK_SOCK_INFO sender) {
            return new SocketInfoDomain();
        }

        /**
         * 根据流信息获取流源
         *
         * @param mediaInfo 流信息
         * @return 流源
         */
        static MK_MEDIA_SOURCE getMediaSourceByMediaInfo(MediaInfoDomain mediaInfo) {
            return getZlmApi()
                    .mk_media_source_find2(mediaInfo.getSchema(), mediaInfo.getVhost(), mediaInfo.getApp(), mediaInfo.getStream(), 0);
        }

        /**
         * 根据流信息获取流源
         *
         * @param mediaInfo 流信息
         * @return 流源
         */
        static MK_MEDIA_SOURCE getMediaSourceByMediaInfo(MK_MEDIA_INFO mediaInfo) {
            return getMediaSourceByMediaInfo(getMediaInfo(mediaInfo));
        }


    }

    /**
     * 配置器，用于配置zlm媒体配置
     */
    interface Configurator {

        /**
         * 获取配置项
         *
         * @param ini 配置
         * @param key 配置项
         * @return 配置值
         */
        static String getConfig(MK_INI ini, String key) {
            return getZlmApi().mk_ini_get_option(ini, key);
        }

        /**
         * 设置配置项
         *
         * @param ini          配置
         * @param key          配置项
         * @param value        配置值
         * @param defaultValue 默认值
         */
        static void setOrDefaultConfig(MK_INI ini, String key, String value, String defaultValue) {
            getZlmApi().mk_ini_set_option(ini, key,
                    (value == null || value.isBlank()) ? defaultValue : value);
        }

        /**
         * 设置配置项
         *
         * @param ini          配置
         * @param key          配置项
         * @param value        配置值
         * @param defaultValue 默认值
         */
        static void setOrDefaultConfig(MK_INI ini, String key, Integer value, Integer defaultValue) {
            getZlmApi().mk_ini_set_option_int(ini, key, value == null ? defaultValue : value);
        }

        /**
         * 设置配置项
         *
         * @param ini    配置
         * @param key    配置项
         * @param value  配置值
         * @param getter 默认配置get方法
         */
        static void setOrDefaultConfig(MK_INI ini, String key, String value, SerializableFunction<ZlmMediaProperties, String> getter) {
            ZlmMediaProperties defaultProperties = getContext().getDefaultProperties();
            String defaultValue = getter.apply(defaultProperties);
            getZlmApi().mk_ini_set_option(ini, key, value == null ? defaultValue : value);
        }

        /**
         * 设置配置项
         *
         * @param ini    配置
         * @param key    配置项
         * @param value  配置值
         * @param getter 默认配置get方法
         */
        static void setOrDefaultConfig(MK_INI ini, String key, Integer value, SerializableFunction<ZlmMediaProperties, Integer> getter) {
            ZlmMediaProperties defaultProperties = getContext().getDefaultProperties();
            Integer defaultValue = getter.apply(defaultProperties);
            getZlmApi().mk_ini_set_option_int(ini, key, value == null ? defaultValue : value);
        }


        /**
         * 设置配置项
         *
         * @param ini    配置
         * @param key    配置项
         * @param value  配置值
         * @param zlmApi zlmApi
         */
        static void setConfig(ZLMApi zlmApi, MK_INI ini, String key, String value) {
            if (value == null || value.isBlank()) {
                return;
            }
            zlmApi.mk_ini_set_option(ini, key, value);
        }

        /**
         * 设置配置项
         *
         * @param ini   配置
         * @param key   配置项
         * @param value 配置值
         */
        static void setConfig(MK_INI ini, String key, String value) {
            setConfig(getZlmApi(), ini, key, value);
        }

        /**
         * 设置配置项
         *
         * @param ini   配置
         * @param key   配置项
         * @param value 配置值
         */
        static void setConfig(MK_INI ini, String key, Integer value) {
            setConfig(getZlmApi(), ini, key, value);
        }

        /**
         * 设置配置项
         *
         * @param ini   配置
         * @param key   配置项
         * @param value 配置值
         */
        static void setConfig(ZLMApi zlmApi, MK_INI ini, String key, Integer value) {
            if (value == null) {
                return;
            }
            zlmApi.mk_ini_set_option_int(ini, key, value);
        }

        /**
         * 设置启用的协议
         *
         * @param ini                  媒体配置
         * @param enabledDemandSchemes 启用的协议
         * @param disableOther         是否禁用其他协议
         */
        static void setSchemeEnableStatus(MK_INI ini, boolean disableOther, SchemeEnum... enabledDemandSchemes) {
            setSchemeEnableStatus(ini, disableOther, new HashSet<>(List.of(enabledDemandSchemes)));
        }

        /**
         * 设置启用的协议
         *
         * @param ini                  媒体配置
         * @param enabledDemandSchemes 启用的协议
         * @param disableOther         是否禁用其他协议
         */
        static void setSchemeEnableStatus(MK_INI ini, boolean disableOther, Set<SchemeEnum> enabledDemandSchemes) {
            if (disableOther) {
                setConfig(ini, "protocol.enable_ts", 0);
                setConfig(ini, "protocol.enable_hls", 0);
                setConfig(ini, "protocol.enable_fmp4", 0);
                setConfig(ini, "protocol.enable_rtsp", 0);
                setConfig(ini, "protocol.enable_rtmp", 0);
                setConfig(ini, "protocol.enable_mp4", 0);
                setConfig(ini, "protocol.enable_hls_fmp4", 0);
            }
            for (SchemeEnum enabledDemandScheme : enabledDemandSchemes) {
                switch (enabledDemandScheme) {
                    case TS -> setConfig(ini, "protocol.enable_ts", 1);
                    case HLS -> setConfig(ini, "protocol.enable_hls", 1);
                    case FMP4 -> setConfig(ini, "protocol.enable_fmp4", 1);
                    case RTSP -> setConfig(ini, "protocol.enable_rtsp", 1);
                    case RTMP -> setConfig(ini, "protocol.enable_rtmp", 1);
                    case HLS_FMP4 -> setConfig(ini, "protocol.enable_hls_fmp4", 1);
                    case MP4 -> setConfig(ini, "protocol.enable_mp4", 1);
                }
            }
        }

        /**
         * 设置系统默认配置
         *
         * @param ini 媒体配置类
         */
        static void setDefaultConfig(MK_INI ini) {
            setConfig(ini, getContext().getDefaultProperties());
        }

        /**
         * 设置媒体配置
         *
         * @param ini        媒体服务配置类
         * @param properties 配置信息
         */
        static void setConfig(MK_INI ini, ZlmMediaProperties properties) {
            setConfig(getZlmApi(), ini, properties);
        }

        /**
         * 设置媒体配置
         *
         * @param ini        媒体服务配置类
         * @param properties 配置信息
         * @param zlmApi     api
         */
        static void setConfig(ZLMApi zlmApi, MK_INI ini, ZlmMediaProperties properties) {
            setConfig(zlmApi, ini, "general.mediaServerId", "SimpleSecretMediaServer");
            setConfig(zlmApi, ini, "general.listen_ip", properties.getListenIp());
            setConfig(zlmApi, ini, "http.notFound", "<h1 style=\"text-align:center;\">SimpleSecret Media Server By Secret丶君</h1>");
            setConfig(zlmApi, ini, "protocol.auto_close", properties.getAutoClose());
            setConfig(zlmApi, ini, "general.streamNoneReaderDelayMS", properties.getStreamNoneReaderDelayMs());
            setConfig(zlmApi, ini, "general.maxStreamWaitMS", properties.getMaxStreamWaitMs());
            setConfig(zlmApi, ini, "protocol.enable_ts", properties.getEnableTs());
            setConfig(zlmApi, ini, "protocol.enable_hls", properties.getEnableHls());
            setConfig(zlmApi, ini, "protocol.enable_fmp4", properties.getEnableFmp4());
            setConfig(zlmApi, ini, "protocol.enable_rtsp", properties.getEnableRtsp());
            setConfig(zlmApi, ini, "protocol.enable_rtmp", properties.getEnableRtmp());
            setConfig(zlmApi, ini, "protocol.enable_mp4", properties.getEnableMp4());
            setConfig(zlmApi, ini, "protocol.enable_hls_fmp4", properties.getEnableHlsFmp4());
            setConfig(zlmApi, ini, "protocol.enable_audio", properties.getEnableAudio());
            setConfig(zlmApi, ini, "protocol.mp4_as_player", properties.getMp4AsPlayer());
            setConfig(zlmApi, ini, "protocol.mp4_max_second", properties.getMp4MaxSecond());
            setConfig(zlmApi, ini, "http.rootPath", properties.getRootPath());
            setConfig(zlmApi, ini, "protocol.mp4_save_path", properties.getMp4SavePath());
            setConfig(zlmApi, ini, "protocol.hls_save_path", properties.getHlsSavePath());
            setConfig(zlmApi, ini, "protocol.hls_demand", properties.getHlsDemand());
            setConfig(zlmApi, ini, "protocol.rtsp_demand", properties.getRtspDemand());
            setConfig(zlmApi, ini, "protocol.rtmp_demand", properties.getRtmpDemand());
            setConfig(zlmApi, ini, "protocol.ts_demand", properties.getTsDemand());
            setConfig(zlmApi, ini, "protocol.fmp4_demand", properties.getFmp4Demand());
            setConfig(zlmApi, ini, "hls.broadcastRecordTs", properties.getBroadcastRecordTs());
            setConfig(zlmApi, ini, "hls.segNum", properties.getHlsSegNum());
            setConfig(zlmApi, ini, "hls.segDur", properties.getHlsSegDur());
            setConfig(zlmApi, ini, "record.fileRepeat", properties.getRecordFileRepeat());
        }
    }
}

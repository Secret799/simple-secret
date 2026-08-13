package com.ss.application.djisei.diagnostic;

import com.ss.application.djisei.config.DjiSeiProperties;
import com.ss.application.djisei.parser.H26xSeiParser;
import com.ss.application.djisei.parser.SeiMessage;
import com.ss.application.djisei.parser.SeiParseIssue;
import com.ss.application.djisei.parser.SeiParseResult;
import com.ss.application.djisei.parser.VideoCodec;
import com.ss.easymedia.callback.TrackDelegateCallback;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.domain.TrackDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DJI RTMP H.264/H.265 视频帧的有界 SEI 诊断回调。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public final class DjiSeiTrackCallback implements TrackDelegateCallback {

    /** 当前诊断回调日志记录器。 */
    private static final Logger LOG = LoggerFactory.getLogger(DjiSeiTrackCallback.class);

    /** 唯一支持的媒体源协议。 */
    private static final String RTMP_SCHEMA = "rtmp";

    /** 标识视频轨道的 ZLMediaKit 整数值。 */
    private static final int VIDEO_TRACK = 1;

    /** Annex-B SEI 解析器。 */
    private final H26xSeiParser parser;

    /** 已校验的诊断配置。 */
    private final DjiSeiProperties properties;

    /** 用于确定汇总周期和流存续时间的时钟。 */
    private final Clock clock;

    /** 按媒体流隔离的线程安全诊断统计。 */
    private final ConcurrentHashMap<StreamKey, StreamDiagnostics> diagnostics = new ConcurrentHashMap<>();

    /**
     * 创建 RTMP SEI 诊断回调。
     *
     * @param parser H.264/H.265 SEI 解析器
     * @param properties 有界诊断配置
     * @param clock 统计汇总时钟
     */
    public DjiSeiTrackCallback(H26xSeiParser parser, DjiSeiProperties properties, Clock clock) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 获取回调支持的媒体源协议。
     *
     * @return 仅包含 RTMP 的不可变集合
     */
    @Override
    public Set<String> supportedSchemas() {
        return Set.of(RTMP_SCHEMA);
    }

    /**
     * 初始化允许的 RTMP 媒体流统计并记录注册事件。
     *
     * @param mediaSource 媒体源信息
     */
    @Override
    public void onMediaSourceRegistered(MediaSourceDomain mediaSource) {
        if (!isAllowedSource(mediaSource)) {
            return;
        }
        StreamKey key = StreamKey.from(mediaSource);
        diagnostics.computeIfAbsent(key, ignored -> newDiagnostics());
        LOG.info("DJI RTMP stream registered: app={}, stream={}", mediaSource.getApp(), mediaSource.getStream());
    }

    /**
     * 删除允许的 RTMP 媒体流统计并记录最终汇总。
     *
     * @param mediaSource 媒体源信息
     */
    @Override
    public void onMediaSourceDeregistered(MediaSourceDomain mediaSource) {
        if (!isAllowedSource(mediaSource)) {
            return;
        }
        StreamDiagnostics removed = diagnostics.remove(StreamKey.from(mediaSource));
        if (removed != null) {
            logSummary("stream summary", mediaSource, removed.snapshot(clock.instant()));
        }
    }

    /**
     * 过滤并同步解析一帧允许的 RTMP 视频数据。
     *
     * @param source 媒体源信息
     * @param track 视频轨道信息
     * @param frame 编码帧信息
     */
    @Override
    public void callback(MediaSourceDomain source, TrackDomain track, TackDelegateInfo frame) {
        if (source == null || track == null || frame == null || !isAllowedSource(source)) {
            return;
        }
        if (!Objects.equals(VIDEO_TRACK, track.getIsVideo())) {
            return;
        }
        Optional<VideoCodec> codec = VideoCodec.fromCodecName(track.getCodecIdName());
        if (codec.isEmpty() || frame.getData() == null) {
            return;
        }
        processAcceptedFrame(source, frame, codec.get());
    }

    /**
     * 解析已经通过过滤的视频帧并更新当前流统计。
     *
     * @param source 媒体源信息
     * @param frame 编码帧信息
     * @param codec 视频编码格式
     */
    private void processAcceptedFrame(MediaSourceDomain source, TackDelegateInfo frame, VideoCodec codec) {
        SeiParseResult result = parser.parse(frame.getData(), codec,
                properties.getMaxFrameBytes(), properties.getMaxPayloadBytes());
        Instant now = clock.instant();
        StreamDiagnostics streamDiagnostics = diagnostics.computeIfAbsent(
                StreamKey.from(source), ignored -> newDiagnostics());
        Optional<DiagnosticsSnapshot> summary = streamDiagnostics.record(result, now, properties.getSummaryInterval());
        logMalformedFrame(source, codec, result.issues());
        for (SeiMessage message : result.messages()) {
            logMessage(source, frame, codec, message);
        }
        summary.ifPresent(snapshot -> logSummary("periodic summary", source, snapshot));
    }

    /**
     * 记录一条解析出的 SEI 消息及其有界预览。
     *
     * @param source 媒体源信息
     * @param frame 编码帧信息
     * @param codec 视频编码格式
     * @param message SEI 消息
     */
    private void logMessage(MediaSourceDomain source, TackDelegateInfo frame, VideoCodec codec, SeiMessage message) {
        byte[] payload = message.payload();
        PayloadPreview preview = PayloadPreview.from(payload, properties.getPreviewBytes());
        LOG.info("DJI RTMP SEI detected: app={}, stream={}, codec={}, pts={}, dts={}, "
                        + "payloadType={}, payloadBytes={}, uuid={}, hex={}, text={}",
                source.getApp(), source.getStream(), codec, frame.getPts(), frame.getDts(), message.payloadType(),
                payload.length, message.uuid().orElse(null), preview.hex(), preview.text());
    }

    /**
     * 每个包含解析问题的帧只记录一次有界告警。
     *
     * @param source 媒体源信息
     * @param codec 视频编码格式
     * @param issues 当前帧的解析问题
     */
    private void logMalformedFrame(MediaSourceDomain source, VideoCodec codec, List<SeiParseIssue> issues) {
        if (issues.isEmpty()) {
            return;
        }
        SeiParseIssue firstIssue = issues.get(0);
        LOG.warn("DJI RTMP malformed SEI frame: app={}, stream={}, codec={}, issueCount={}, "
                        + "firstIssueCode={}, firstIssueMessage={}",
                source.getApp(), source.getStream(), codec, issues.size(), firstIssue.code(), firstIssue.message());
    }

    /**
     * 记录周期或注销统计汇总。
     *
     * @param summaryType 汇总类型
     * @param source 媒体源信息
     * @param snapshot 不可变统计快照
     */
    private void logSummary(String summaryType, MediaSourceDomain source, DiagnosticsSnapshot snapshot) {
        LOG.info("DJI RTMP {}: app={}, stream={}, videoFrames={}, seiNalUnits={}, seiMessages={}, "
                        + "malformedMessages={}, elapsedMs={}",
                summaryType, source.getApp(), source.getStream(), snapshot.videoFrames,
                snapshot.seiNalUnits, snapshot.seiMessages, snapshot.malformedMessages, snapshot.elapsedMillis);
    }

    /**
     * 判断媒体源是否属于允许的 RTMP 应用。
     *
     * @param source 媒体源信息
     * @return 媒体源可由当前回调处理时返回 true
     */
    private boolean isAllowedSource(MediaSourceDomain source) {
        return source != null && RTMP_SCHEMA.equals(source.getSchema())
                && Objects.equals(properties.getAllowedApp(), source.getApp());
    }

    /**
     * 使用当前时间创建流统计。
     *
     * @return 新的线程安全流统计
     */
    private StreamDiagnostics newDiagnostics() {
        return new StreamDiagnostics(clock.instant(), properties.getSummaryInterval());
    }

    /**
     * 不可变的媒体流标识。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    private static final class StreamKey {

        /** ZLMediaKit 应用名。 */
        private final String app;

        /** ZLMediaKit 流标识。 */
        private final String stream;

        /**
         * 创建媒体流标识。
         *
         * @param app ZLMediaKit 应用名
         * @param stream ZLMediaKit 流标识
         */
        private StreamKey(String app, String stream) {
            this.app = app;
            this.stream = stream;
        }

        /**
         * 从媒体源创建流标识。
         *
         * @param source 媒体源信息
         * @return 不可变流标识
         */
        private static StreamKey from(MediaSourceDomain source) {
            return new StreamKey(source.getApp(), source.getStream());
        }

        /**
         * 比较媒体流标识。
         *
         * @param object 待比较对象
         * @return 应用名和流标识相同时返回 true
         */
        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof StreamKey streamKey)) {
                return false;
            }
            return Objects.equals(app, streamKey.app) && Objects.equals(stream, streamKey.stream);
        }

        /** @return 基于应用名和流标识的哈希值 */
        @Override
        public int hashCode() {
            return Objects.hash(app, stream);
        }
    }

    /**
     * 单个媒体流的同步饱和统计。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    private static final class StreamDiagnostics {

        /** 当前统计周期的开始时间。 */
        private final Instant startedAt;

        /** 下次允许输出周期汇总的时间。 */
        private Instant nextSummaryAt;

        /** 已接受的视频帧数。 */
        private long videoFrames;

        /** 已识别的 SEI NAL 单元数。 */
        private long seiNalUnits;

        /** 已解析的 SEI 消息数。 */
        private long seiMessages;

        /** 累计解析问题数。 */
        private long malformedMessages;

        /**
         * 创建单流统计。
         *
         * @param startedAt 统计开始时间
         * @param summaryInterval 周期汇总间隔
         */
        private StreamDiagnostics(Instant startedAt, Duration summaryInterval) {
            this.startedAt = startedAt;
            this.nextSummaryAt = startedAt.plus(summaryInterval);
        }

        /**
         * 原子更新帧统计，并在到达周期时返回快照。
         *
         * @param result 当前帧解析结果
         * @param now 当前时间
         * @param summaryInterval 周期汇总间隔
         * @return 到达汇总周期时返回统计快照，否则为空
         */
        private synchronized Optional<DiagnosticsSnapshot> record(
                SeiParseResult result, Instant now, Duration summaryInterval) {
            videoFrames = saturatedAdd(videoFrames, 1);
            seiNalUnits = saturatedAdd(seiNalUnits, result.seiNalUnitCount());
            seiMessages = saturatedAdd(seiMessages, result.messages().size());
            malformedMessages = saturatedAdd(malformedMessages, result.issues().size());
            if (now.isBefore(nextSummaryAt)) {
                return Optional.empty();
            }
            nextSummaryAt = now.plus(summaryInterval);
            return Optional.of(createSnapshot(now));
        }

        /**
         * 获取当前统计的原子快照。
         *
         * @param now 当前时间
         * @return 不可变统计快照
         */
        private synchronized DiagnosticsSnapshot snapshot(Instant now) {
            return createSnapshot(now);
        }

        /**
         * 创建统计快照。
         *
         * @param now 当前时间
         * @return 不可变统计快照
         */
        private DiagnosticsSnapshot createSnapshot(Instant now) {
            long elapsedMillis = Math.max(0L, Duration.between(startedAt, now).toMillis());
            return new DiagnosticsSnapshot(videoFrames, seiNalUnits, seiMessages,
                    malformedMessages, elapsedMillis);
        }

        /**
         * 执行不会溢出的正数累加。
         *
         * @param current 当前计数
         * @param increment 非负增量
         * @return 未溢出的累加结果，达到上限时为 Long.MAX_VALUE
         */
        private static long saturatedAdd(long current, long increment) {
            return increment >= Long.MAX_VALUE - current ? Long.MAX_VALUE : current + increment;
        }
    }

    /**
     * 单个媒体流的不可变统计快照。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    private static final class DiagnosticsSnapshot {

        /** 已接受的视频帧数。 */
        private final long videoFrames;

        /** 已识别的 SEI NAL 单元数。 */
        private final long seiNalUnits;

        /** 已解析的 SEI 消息数。 */
        private final long seiMessages;

        /** 累计解析问题数。 */
        private final long malformedMessages;

        /** 统计存续毫秒数。 */
        private final long elapsedMillis;

        /**
         * 创建不可变统计快照。
         *
         * @param videoFrames 已接受的视频帧数
         * @param seiNalUnits 已识别的 SEI NAL 单元数
         * @param seiMessages 已解析的 SEI 消息数
         * @param malformedMessages 累计解析问题数
         * @param elapsedMillis 统计存续毫秒数
         */
        private DiagnosticsSnapshot(long videoFrames, long seiNalUnits, long seiMessages,
                                    long malformedMessages, long elapsedMillis) {
            this.videoFrames = videoFrames;
            this.seiNalUnits = seiNalUnits;
            this.seiMessages = seiMessages;
            this.malformedMessages = malformedMessages;
            this.elapsedMillis = elapsedMillis;
        }
    }
}

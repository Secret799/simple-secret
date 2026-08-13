package com.ss.application.djisei.diagnostic;

import com.ss.application.djisei.config.DjiSeiProperties;
import com.ss.application.djisei.parser.H26xSeiParser;
import com.ss.application.djisei.parser.SeiMessage;
import com.ss.application.djisei.parser.SeiParseIssue;
import com.ss.application.djisei.parser.SeiParseLimits;
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
import java.util.concurrent.atomic.AtomicReference;

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

    /** Annex-B SEI 帧解析函数。 */
    private final FrameParser parser;

    /** 已校验的诊断配置。 */
    private final DjiSeiProperties properties;

    /** 用于确定汇总周期和流存续时间的时钟。 */
    private final Clock clock;

    /** 按媒体流隔离的线程安全生命周期。 */
    private final ConcurrentHashMap<StreamKey, StreamLifecycle> lifecycles = new ConcurrentHashMap<>();

    /**
     * 创建 RTMP SEI 诊断回调。
     *
     * @param parser H.264/H.265 SEI 解析器
     * @param properties 有界诊断配置
     * @param clock 统计汇总时钟
     */
    public DjiSeiTrackCallback(H26xSeiParser parser, DjiSeiProperties properties, Clock clock) {
        this(parserWithLimits(parser, properties), properties, clock);
    }

    /**
     * 创建可控制解析时序的 RTMP SEI 诊断回调。
     *
     * @param parser H.264/H.265 SEI 帧解析函数
     * @param properties 有界诊断配置
     * @param clock 统计汇总时钟
     */
    DjiSeiTrackCallback(FrameParser parser, DjiSeiProperties properties, Clock clock) {
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
        AtomicReference<StreamLifecycle> replaced = new AtomicReference<>();
        lifecycles.compute(key, (ignored, current) -> replaceLifecycle(current, mediaSource, replaced));
        closeAndLogReplacedLifecycle(mediaSource, replaced.get());
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
        StreamKey key = StreamKey.from(mediaSource);
        StreamLifecycle current = lifecycles.get(key);
        if (current != null && current.belongsTo(mediaSource) && lifecycles.remove(key, current)) {
            logSummary("stream summary", mediaSource, current.close(clock.instant()));
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
        StreamLifecycle lifecycle = admitLifecycle(source);
        if (lifecycle != null) {
            processAcceptedFrame(source, frame, codec.get(), lifecycle);
        }
    }

    /**
     * 解析已经通过过滤的视频帧并更新当前流统计。
     *
     * @param source 媒体源信息
     * @param frame 编码帧信息
     * @param codec 视频编码格式
     * @param lifecycle 已准入的媒体流生命周期
     */
    private void processAcceptedFrame(MediaSourceDomain source, TackDelegateInfo frame, VideoCodec codec,
                                      StreamLifecycle lifecycle) {
        try {
            SeiParseResult result = parser.parse(frame.getData(), codec,
                    properties.getMaxFrameBytes(), properties.getMaxPayloadBytes());
            Optional<DiagnosticsSnapshot> summary = lifecycle.record(
                    result, clock.instant(), properties.getSummaryInterval());
            logMalformedFrame(source, codec, result.issues());
            int logCount = Math.min(result.messages().size(), properties.getMaxMessageLogs());
            for (int index = 0; index < logCount; index++) {
                logMessage(source, frame, codec, result.messages().get(index));
            }
            summary.ifPresent(snapshot -> logSummary("periodic summary", source, snapshot));
        } finally {
            lifecycle.release();
        }
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
     * 将应用配置转换为解析器的单帧资源上限。
     *
     * @param parser H.264/H.265 SEI 解析器
     * @param properties 已校验的诊断配置
     * @return 兼容测试注入接口的有界解析函数
     */
    private static FrameParser parserWithLimits(H26xSeiParser parser, DjiSeiProperties properties) {
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(properties, "properties");
        return (frame, codec, maxFrameBytes, maxPayloadBytes) -> parser.parse(frame, codec, maxFrameBytes,
                new SeiParseLimits(maxPayloadBytes, properties.getMaxSeiNalUnits(),
                        properties.getMaxSeiMessages(), properties.getMaxParseIssues()));
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
     * @param source 注册时的媒体源 token
     * @return 新的线程安全流统计
     */
    private StreamLifecycle newLifecycle(MediaSourceDomain source) {
        return new StreamLifecycle(source, clock.instant(), properties.getSummaryInterval());
    }

    /**
     * 原子选择或替换指定媒体流的注册代次。
     *
     * @param current 当前注册代次
     * @param source 新注册媒体源 token
     * @param replaced 被替换代次接收器
     * @return 应保留在生命周期映射中的代次
     */
    private StreamLifecycle replaceLifecycle(StreamLifecycle current, MediaSourceDomain source,
                                             AtomicReference<StreamLifecycle> replaced) {
        if (current == null || current.belongsTo(source)) {
            return current == null ? newLifecycle(source) : current;
        }
        replaced.set(current);
        return newLifecycle(source);
    }

    /**
     * 在映射锁外关闭被新注册事件替换的旧代次。
     *
     * @param source 新注册媒体源信息
     * @param replaced 被替换的旧代次
     */
    private void closeAndLogReplacedLifecycle(MediaSourceDomain source, StreamLifecycle replaced) {
        if (replaced != null) {
            logSummary("stream summary", source, replaced.close(clock.instant()));
        }
    }

    /**
     * 为匹配当前注册 token 的帧获取生命周期准入。
     *
     * @param source 帧所属媒体源 token
     * @return 已增加在途计数的生命周期；未注册、过期或已关闭时为 null
     */
    private StreamLifecycle admitLifecycle(MediaSourceDomain source) {
        StreamLifecycle lifecycle = lifecycles.get(StreamKey.from(source));
        return lifecycle != null && lifecycle.belongsTo(source) && lifecycle.admit() ? lifecycle : null;
    }

    /**
     * 可注入的同步有界视频帧解析函数。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    @FunctionalInterface
    interface FrameParser {

        /**
         * 解析单个有界视频帧。
         *
         * @param frame Annex-B 视频帧
         * @param codec 视频编码格式
         * @param maxFrameBytes 单帧最大字节数
         * @param maxPayloadBytes 单条负载最大字节数
         * @return SEI 解析结果
         */
        SeiParseResult parse(byte[] frame, VideoCodec codec, int maxFrameBytes, int maxPayloadBytes);
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
    private static final class StreamLifecycle {

        /** 当前注册代次使用的媒体源 identity token。 */
        private final MediaSourceDomain sourceToken;

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

        /** 当前正在解析或输出日志的已准入帧数。 */
        private int admittedFrames;

        /** 当前注册代次是否仍接受新帧。 */
        private boolean open = true;

        /**
         * 创建单流统计。
         *
         * @param sourceToken 注册时的媒体源 identity token
         * @param startedAt 统计开始时间
         * @param summaryInterval 周期汇总间隔
         */
        private StreamLifecycle(MediaSourceDomain sourceToken, Instant startedAt, Duration summaryInterval) {
            this.sourceToken = sourceToken;
            this.startedAt = startedAt;
            this.nextSummaryAt = startedAt.plus(summaryInterval);
        }

        /**
         * 判断事件是否属于当前注册代次。
         *
         * @param source 媒体源 identity token
         * @return 与注册时对象相同时返回 true
         */
        private boolean belongsTo(MediaSourceDomain source) {
            return sourceToken == source;
        }

        /**
         * 在生命周期关闭前准入一个视频帧。
         *
         * @return 成功增加在途帧计数时返回 true
         */
        private synchronized boolean admit() {
            if (!open || admittedFrames == Integer.MAX_VALUE) {
                return false;
            }
            admittedFrames++;
            return true;
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
         * 释放一个已经完成解析和日志输出的准入帧。
         */
        private synchronized void release() {
            admittedFrames--;
            if (admittedFrames == 0) {
                notifyAll();
            }
        }

        /**
         * 关闭生命周期，等待全部准入帧完成并获取最终快照。
         *
         * @param now 当前时间
         * @return 不可变统计快照
         */
        private synchronized DiagnosticsSnapshot close(Instant now) {
            open = false;
            boolean interrupted = false;
            while (admittedFrames > 0) {
                try {
                    wait();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
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

package com.ss.application.pushstream.status;

import java.time.Instant;
import java.util.List;

/**
 * 推流应用只读状态视图。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public final class PublishStreamStatusView {

    /** 状态生成时间。 */
    private final Instant generatedAt;

    /** 当前扫描文件状态。 */
    private final List<PublishStreamStatus> streams;

    /** 最近一次状态查询时媒体服务是否可达。 */
    private final boolean mediaServerReachable;

    /**
     * 创建状态视图。
     *
     * @param generatedAt 状态生成时间
     * @param streams 文件状态列表
     */
    public PublishStreamStatusView(Instant generatedAt, List<PublishStreamStatus> streams) {
        this(generatedAt, streams, true);
    }

    /**
     * 创建包含媒体服务健康状态的视图。
     *
     * @param generatedAt 状态生成时间
     * @param streams 文件状态列表
     * @param mediaServerReachable 媒体服务是否可达
     */
    public PublishStreamStatusView(
            Instant generatedAt, List<PublishStreamStatus> streams,
            boolean mediaServerReachable) {
        this.generatedAt = generatedAt;
        this.streams = List.copyOf(streams);
        this.mediaServerReachable = mediaServerReachable;
    }

    /** @return 状态生成时间 */
    public Instant getGeneratedAt() {
        return generatedAt;
    }

    /** @return 文件状态列表 */
    public List<PublishStreamStatus> getStreams() {
        return streams;
    }

    /** @return 最近一次状态查询时媒体服务是否可达 */
    public boolean isMediaServerReachable() {
        return mediaServerReachable;
    }
}

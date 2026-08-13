package com.ss.application.pushstream.status;

import java.time.Instant;

/**
 * 可通过状态接口展示的推流状态，不包含本地绝对路径。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public final class PublishStreamStatus {

    /** 文件名。 */
    private final String fileName;

    /** 稳定流标识。 */
    private final String streamId;

    /** 当前状态。 */
    private final PublishStreamState state;

    /** 文件大小，单位为字节。 */
    private final long size;

    /** 文件最后修改时间。 */
    private final Instant lastModifiedTime;

    /** 已脱敏的最近错误说明。 */
    private final String errorMessage;

    /**
     * 创建推流状态。
     *
     * @param fileName 文件名
     * @param streamId 流标识
     * @param state 当前状态
     * @param size 文件大小
     * @param lastModifiedTime 最后修改时间
     * @param errorMessage 已脱敏错误说明
     */
    public PublishStreamStatus(String fileName, String streamId, PublishStreamState state,
                               long size, Instant lastModifiedTime, String errorMessage) {
        this.fileName = fileName;
        this.streamId = streamId;
        this.state = state;
        this.size = size;
        this.lastModifiedTime = lastModifiedTime;
        this.errorMessage = errorMessage;
    }

    /** @return 文件名 */
    public String getFileName() {
        return fileName;
    }

    /** @return 流标识 */
    public String getStreamId() {
        return streamId;
    }

    /** @return 当前状态 */
    public PublishStreamState getState() {
        return state;
    }

    /** @return 文件大小 */
    public long getSize() {
        return size;
    }

    /** @return 最后修改时间 */
    public Instant getLastModifiedTime() {
        return lastModifiedTime;
    }

    /** @return 已脱敏错误说明 */
    public String getErrorMessage() {
        return errorMessage;
    }
}

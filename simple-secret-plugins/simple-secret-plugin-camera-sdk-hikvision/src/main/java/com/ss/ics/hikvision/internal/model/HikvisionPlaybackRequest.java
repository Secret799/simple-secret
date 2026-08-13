package com.ss.ics.hikvision.internal.model;

import java.time.LocalDateTime;

/**
 * 海康按时间回放的内部原生参数。
 *
 * @param channel 原生通道号
 * @param streamType 码流类型
 * @param beginTime 回放开始时间
 * @param endTime 回放结束时间
 * @author junpzx
 * @since 2026-08-12
 */
public record HikvisionPlaybackRequest(
        int channel, int streamType, LocalDateTime beginTime, LocalDateTime endTime) {
}

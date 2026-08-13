package com.ss.ics.hikvision.internal.model;

/**
 * 海康实时预览的内部原生参数。
 *
 * @param channel 原生通道号
 * @param streamType 码流类型
 * @param protocolType 应用层取流协议类型
 * @author junpzx
 * @since 2026-08-12
 */
public record HikvisionPreviewRequest(int channel, int streamType, int protocolType) {
}

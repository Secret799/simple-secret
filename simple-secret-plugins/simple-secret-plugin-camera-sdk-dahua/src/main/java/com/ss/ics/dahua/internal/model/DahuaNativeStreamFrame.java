package com.ss.ics.dahua.internal.model;

/**
 * 原生回调数据的驱动内部快照。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public record DahuaNativeStreamFrame(
        byte[] data, long pts, long dts, int frameType, int frameSubType) {
    /**
     * 创建与原生内存分离的码流帧。
     *
     * @param data 码流字节
     * @param pts 显示时间戳
     * @param dts 解码时间戳
     * @param frameType 帧类型
     * @param frameSubType 帧子类型
     */
    public DahuaNativeStreamFrame {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}

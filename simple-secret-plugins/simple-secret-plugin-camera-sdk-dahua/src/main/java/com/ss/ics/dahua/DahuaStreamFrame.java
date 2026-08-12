package com.ss.ics.dahua;

/** 已复制的 H.264 Annex-B 帧。 */
public record DahuaStreamFrame(
        byte[] data, long pts, long dts, int frameType, int frameSubType) {
    /**
     * 防止调用者持有或修改内部帧缓冲区。
     *
     * @param data 业务数据
     * @param pts 显示时间戳
     * @param dts 解码时间戳
     * @param frameType 厂商 SDK 帧类型
     * @param frameSubType 厂商 SDK 帧子类型
     */
    public DahuaStreamFrame {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data must not be empty");
        }
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}

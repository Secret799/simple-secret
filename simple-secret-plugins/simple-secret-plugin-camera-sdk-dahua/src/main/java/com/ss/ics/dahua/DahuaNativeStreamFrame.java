package com.ss.ics.dahua;

/** 原生回调数据的驱动内部快照。 */
record DahuaNativeStreamFrame(
        byte[] data, long pts, long dts, int frameType, int frameSubType) {
    DahuaNativeStreamFrame {
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

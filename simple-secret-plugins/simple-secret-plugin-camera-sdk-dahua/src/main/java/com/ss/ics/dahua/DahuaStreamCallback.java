package com.ss.ics.dahua;

/** 消费大华实时 H.264 Annex-B 码流帧。 */
@FunctionalInterface
public interface DahuaStreamCallback {
    /** @param frame 已脱离 native 内存生命周期的帧快照 */
    void onFrame(DahuaStreamFrame frame);
}

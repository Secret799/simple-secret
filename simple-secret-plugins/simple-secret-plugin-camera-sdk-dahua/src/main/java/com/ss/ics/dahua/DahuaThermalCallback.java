package com.ss.ics.dahua;

/** 消费大华热成像帧。 */
@FunctionalInterface
public interface DahuaThermalCallback {
    /** @param data 已脱离 native 内存生命周期的热成像快照 */
    void onData(DahuaThermalData data);
}

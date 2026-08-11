package com.ss.ics.dahua;

/** 驱动内部的已解析热成像回调。 */
@FunctionalInterface
interface DahuaNativeThermalCallback {
    void onData(DahuaNativeThermalData data);
}

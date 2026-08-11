package com.ss.ics.dahua;

/** 驱动内部的已复制原生码流回调。 */
@FunctionalInterface
interface DahuaNativeStreamCallback {
    void onFrame(DahuaNativeStreamFrame frame);
}

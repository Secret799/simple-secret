package com.ss.ics.dahua.internal;

import com.ss.ics.dahua.internal.model.DahuaNativeStreamFrame;

/**
 * 驱动内部的已复制原生码流回调。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@FunctionalInterface
public interface DahuaNativeStreamCallback {
    /**
     * 消费已经与原生内存分离的码流帧。
     *
     * @param frame 码流帧
     */
    void onFrame(DahuaNativeStreamFrame frame);
}

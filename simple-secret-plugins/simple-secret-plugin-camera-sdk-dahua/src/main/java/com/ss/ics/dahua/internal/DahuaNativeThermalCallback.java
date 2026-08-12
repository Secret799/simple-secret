package com.ss.ics.dahua.internal;

import com.ss.ics.dahua.internal.model.DahuaNativeThermalData;

/**
 * 驱动内部的已解析热成像回调。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@FunctionalInterface
public interface DahuaNativeThermalCallback {
    /**
     * 消费已经与原生内存分离的热成像数据。
     *
     * @param data 热成像数据
     */
    void onData(DahuaNativeThermalData data);
}

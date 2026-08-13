package com.ss.ics.hikvision;

/**
 * 消费已经脱离海康原生回调内存生命周期的码流数据。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@FunctionalInterface
public interface HikvisionStreamDataHandler {

    /**
     * 处理一个码流数据块。
     *
     * @param data 码流数据快照
     */
    void onData(HikvisionStreamData data);
}

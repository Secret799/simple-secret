package com.ss.ics.hikvision.internal;

/**
 * 海康原生取流数据的内部回调边界。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@FunctionalInterface
public interface HikvisionNativeStreamCallback {

    /**
     * 处理已经复制出原生内存的数据。
     *
     * @param streamHandle 原生播放句柄
     * @param dataType 海康 SDK 数据类型
     * @param data 字节数据快照
     */
    void onData(long streamHandle, int dataType, byte[] data);
}

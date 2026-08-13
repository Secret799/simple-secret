package com.ss.ics.hikvision.internal.jna;

import com.sun.jna.Callback;
import com.sun.jna.Pointer;

/**
 * HCNetSDK 实时预览和历史回放的原生字节流回调。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@FunctionalInterface
interface HikvisionNativeDataCallback extends Callback {

    /**
     * 接收原生数据；缓冲区只在本次回调期间有效。
     *
     * @param streamHandle 原生播放句柄
     * @param dataType 海康 SDK 数据类型
     * @param buffer 原生缓冲区
     * @param bufferSize 缓冲区长度
     * @param userData 用户数据
     */
    void invoke(int streamHandle, int dataType, Pointer buffer, int bufferSize, Pointer userData);
}

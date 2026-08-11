package com.ss.ics.service;

import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PlayDomain;
import com.ss.ics.exception.UnsupportedCameraSdkOperationException;

/**
 * 厂商 SDK 实时预览和历史回放能力。
 *
 * @param <T> 操作返回类型
 * @param <R> 目标或适配器参数类型
 */
public interface PlayService<T, R> extends CameraSdkService {

    /**
     * @param device 设备
     * @param request 播放参数
     * @param target 目标或适配器参数
     * @return 播放结果
     */
    default T realPlay(DeviceDomain device, PlayDomain request, R target) {
        throw new UnsupportedCameraSdkOperationException("Real-time preview is not supported");
    }

    /**
     * @param device 设备
     * @param request 播放参数
     * @param target 目标或适配器参数
     * @return 回放结果
     */
    default T playback(DeviceDomain device, PlayDomain request, R target) {
        throw new UnsupportedCameraSdkOperationException("Playback is not supported");
    }
}

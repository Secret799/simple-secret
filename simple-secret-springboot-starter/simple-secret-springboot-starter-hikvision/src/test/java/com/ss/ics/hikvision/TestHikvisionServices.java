package com.ss.ics.hikvision;

import com.ss.ics.hikvision.internal.HikvisionNativeApi;

import java.util.concurrent.atomic.AtomicInteger;

/** Starter 测试使用的无原生库海康服务。 */
public final class TestHikvisionServices {

    private TestHikvisionServices() {
    }

    /**
     * @param options SDK 配置
     * @param cleanupCalls cleanup 调用计数器
     * @return 使用内存 native API 的服务
     */
    public static HikvisionCameraSdkService create(
            HikvisionSdkOptions options, AtomicInteger cleanupCalls) {
        HikvisionNativeApi nativeApi = new HikvisionNativeApi() {
            @Override
            public boolean initialize() {
                return true;
            }

            @Override
            public boolean cleanup() {
                cleanupCalls.incrementAndGet();
                return true;
            }

            @Override
            public int lastError() {
                return 0;
            }
        };
        return HikvisionCameraSdkService.createForTesting(
                HikvisionSdkRuntime.openForTesting(options, nativeApi));
    }
}

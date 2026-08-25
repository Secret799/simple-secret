package com.ss.ics.dahua;

import com.ss.ics.dahua.internal.DahuaNativeApi;

import java.util.concurrent.atomic.AtomicInteger;

/** Starter 测试使用的无原生库大华服务。 */
public final class TestDahuaServices {

    private TestDahuaServices() {
    }

    /**
     * @param options SDK 配置
     * @param cleanupCalls cleanup 调用计数器
     * @return 使用内存 native API 的服务
     */
    public static DahuaCameraSdkService create(
            DahuaSdkOptions options, AtomicInteger cleanupCalls) {
        DahuaNativeApi nativeApi = new DahuaNativeApi() {
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
        return DahuaCameraSdkService.createForTesting(
                DahuaSdkRuntime.openForTesting(options, nativeApi));
    }
}

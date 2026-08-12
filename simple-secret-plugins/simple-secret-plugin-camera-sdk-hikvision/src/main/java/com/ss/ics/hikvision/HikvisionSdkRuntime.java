package com.ss.ics.hikvision;

import com.ss.ics.hikvision.internal.HikvisionNativeApi;
import com.ss.ics.hikvision.internal.jna.JnaHikvisionNativeApi;

import java.util.concurrent.atomic.AtomicBoolean;

/** 海康 SDK 的显式、幂等生命周期。 */
public final class HikvisionSdkRuntime implements AutoCloseable {
    private static final AtomicBoolean PROCESS_RUNTIME_OPEN = new AtomicBoolean();

    private final HikvisionSdkOptions options;
    private final HikvisionNativeApi nativeApi;
    private boolean closed;

    private HikvisionSdkRuntime(HikvisionSdkOptions options, HikvisionNativeApi nativeApi) {
        this.options = options;
        this.nativeApi = nativeApi;
    }

    /**
     * 校验原生库、加载 JNA 接口并初始化 SDK。
     *
     * @param options SDK 配置
     * @return 已初始化的运行时
     */
    public static HikvisionSdkRuntime open(HikvisionSdkOptions options) {
        return openForTesting(options, JnaHikvisionNativeApi.load(options));
    }

    static HikvisionSdkRuntime openForTesting(
            HikvisionSdkOptions options, HikvisionNativeApi nativeApi) {
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }
        if (nativeApi == null) {
            throw new IllegalArgumentException("nativeApi must not be null");
        }
        if (!PROCESS_RUNTIME_OPEN.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "Only one Hikvision SDK runtime may be open per process");
        }
        boolean initialized = false;
        try {
            if (!nativeApi.initialize()) {
                throw new HikvisionSdkException(
                        "Hikvision SDK initialization failed", nativeApi.lastError());
            }
            initialized = true;
            return new HikvisionSdkRuntime(options, nativeApi);
        } finally {
            if (!initialized) {
                PROCESS_RUNTIME_OPEN.set(false);
            }
        }
    }

    /** @return 当前运行时配置 */
    public HikvisionSdkOptions options() {
        return options;
    }

    HikvisionNativeApi nativeApi() {
        return nativeApi;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (!nativeApi.cleanup()) {
            throw new HikvisionSdkException(
                    "Hikvision SDK cleanup failed", nativeApi.lastError());
        }
        closed = true;
        PROCESS_RUNTIME_OPEN.set(false);
    }
}

package com.ss.ics.dahua;

import java.util.concurrent.atomic.AtomicBoolean;

/** 大华 SDK 的显式、幂等生命周期。 */
public final class DahuaSdkRuntime implements AutoCloseable {
    private static final AtomicBoolean PROCESS_RUNTIME_OPEN = new AtomicBoolean();

    private final DahuaSdkOptions options;
    private final DahuaNativeApi nativeApi;
    private boolean closed;

    private DahuaSdkRuntime(DahuaSdkOptions options, DahuaNativeApi nativeApi) {
        this.options = options;
        this.nativeApi = nativeApi;
    }

    /**
     * @param options SDK 配置
     * @return 已初始化的运行时
     */
    public static DahuaSdkRuntime open(DahuaSdkOptions options) {
        return openForTesting(options, JnaDahuaNativeApi.load(options));
    }

    static DahuaSdkRuntime openForTesting(DahuaSdkOptions options, DahuaNativeApi nativeApi) {
        if (options == null) {
            throw new IllegalArgumentException("options must not be null");
        }
        if (nativeApi == null) {
            throw new IllegalArgumentException("nativeApi must not be null");
        }
        if (!PROCESS_RUNTIME_OPEN.compareAndSet(false, true)) {
            throw new IllegalStateException("Only one Dahua SDK runtime may be open per process");
        }
        boolean initialized = false;
        try {
            if (!nativeApi.initialize()) {
                throw new DahuaSdkException(
                        "Dahua SDK initialization failed", nativeApi.lastError());
            }
            initialized = true;
            return new DahuaSdkRuntime(options, nativeApi);
        } finally {
            if (!initialized) {
                PROCESS_RUNTIME_OPEN.set(false);
            }
        }
    }

    /** @return 当前运行时配置 */
    public DahuaSdkOptions options() {
        return options;
    }

    DahuaNativeApi nativeApi() {
        return nativeApi;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (!nativeApi.cleanup()) {
            throw new DahuaSdkException("Dahua SDK cleanup failed", nativeApi.lastError());
        }
        closed = true;
        PROCESS_RUNTIME_OPEN.set(false);
    }
}

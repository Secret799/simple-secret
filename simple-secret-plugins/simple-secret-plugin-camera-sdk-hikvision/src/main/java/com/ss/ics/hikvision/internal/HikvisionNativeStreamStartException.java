package com.ss.ics.hikvision.internal;

/**
 * 原生取流已创建但启动失败清理未完成时的内部异常。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public final class HikvisionNativeStreamStartException extends RuntimeException {
    /** 尚需停止的原生播放句柄。 */
    private final long streamHandle;

    /**
     * 创建启动清理失败异常。
     *
     * @param streamHandle 尚需停止的原生播放句柄
     */
    public HikvisionNativeStreamStartException(long streamHandle) {
        super("Hikvision native stream startup cleanup failed");
        this.streamHandle = streamHandle;
    }

    /**
     * 获取尚需停止的原生播放句柄。
     *
     * @return 原生播放句柄
     */
    public long streamHandle() {
        return streamHandle;
    }
}

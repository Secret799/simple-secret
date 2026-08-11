package com.ss.ics.dahua;

/** 大华实时预览句柄；关闭时停止预览并释放其临时登录。 */
public final class DahuaRealPlaySession implements AutoCloseable {
    private final long handle;
    private final Runnable closeAction;
    private boolean closed;

    DahuaRealPlaySession(long handle, Runnable closeAction) {
        this.handle = handle;
        this.closeAction = closeAction;
    }

    /** @return 厂商实时预览句柄 */
    public long handle() {
        return handle;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closeAction.run();
        closed = true;
    }
}

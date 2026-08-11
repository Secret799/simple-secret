package com.ss.ics.dahua;

import java.util.function.IntSupplier;

/** 热成像订阅句柄。 */
public final class DahuaThermalSubscription implements AutoCloseable {
    private final long handle;
    private final IntSupplier fetchAction;
    private final Runnable closeAction;
    private boolean closed;

    DahuaThermalSubscription(long handle, IntSupplier fetchAction, Runnable closeAction) {
        this.handle = handle;
        this.fetchAction = fetchAction;
        this.closeAction = closeAction;
    }

    /** @return 厂商热成像订阅句柄 */
    public long handle() {
        return handle;
    }

    /** @return 0 未知、1 空闲、2 正在获取热图 */
    public synchronized int fetch() {
        if (closed) {
            throw new IllegalStateException("Dahua thermal subscription is closed");
        }
        return fetchAction.getAsInt();
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

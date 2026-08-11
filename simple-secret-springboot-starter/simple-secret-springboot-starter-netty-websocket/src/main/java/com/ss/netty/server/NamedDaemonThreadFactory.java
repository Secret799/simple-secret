package com.ss.netty.server;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** 创建带稳定前缀的 daemon 线程。 */
final class NamedDaemonThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger sequence = new AtomicInteger();

    NamedDaemonThreadFactory(String prefix) {
        this.prefix = Objects.requireNonNull(prefix, "prefix must not be null");
    }

    @Override
    public Thread newThread(Runnable task) {
        Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }
}

package com.ss.core.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * 能记录任务内部异常的定时线程池。
 *
 * <p>{@link ScheduledThreadPoolExecutor} 会把任务异常保存在 {@link Future} 中，
 * 此实现同时使用 JDK System Logger 输出未被调用方观察到的失败。</p>
 */
public class LoggingScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

    private static final System.Logger LOGGER =
            System.getLogger(LoggingScheduledThreadPoolExecutor.class.getName());

    /**
     * 创建定时线程池。
     *
     * @param corePoolSize 核心线程数
     * @param threadFactory 线程工厂
     * @param handler 拒绝策略
     */
    public LoggingScheduledThreadPoolExecutor(
            int corePoolSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, threadFactory, handler);
    }

    @Override
    protected void afterExecute(Runnable runnable, Throwable throwable) {
        super.afterExecute(runnable, throwable);
        Throwable failure = throwable;
        if (failure == null && runnable instanceof Future<?> future && future.isDone()) {
            try {
                future.get();
            } catch (CancellationException ignored) {
                return;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failure = exception;
            } catch (ExecutionException exception) {
                failure = exception.getCause();
            }
        }
        if (failure != null) {
            LOGGER.log(System.Logger.Level.ERROR, "定时任务执行失败", failure);
        }
    }
}

package com.ss.application.pushstream.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用单线程调度器周期同步媒体文件，避免扫描任务重入。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public class PublishStreamScheduler implements SmartLifecycle {

    /** 应用日志记录器。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(PublishStreamScheduler.class);

    /** 推流应用服务。 */
    private final PublishStreamService publishStreamService;

    /** 受控单线程调度器。 */
    private final ScheduledExecutorService executorService;

    /** 两次扫描之间的固定延迟。 */
    private final Duration scanInterval;

    /** 生命周期运行标识。 */
    private final AtomicBoolean running = new AtomicBoolean();

    /** 当前调度任务。 */
    private volatile ScheduledFuture<?> scheduledFuture;

    /**
     * 创建推流扫描调度器。
     *
     * @param publishStreamService 推流应用服务
     * @param executorService 单线程调度器
     * @param scanInterval 扫描间隔
     */
    public PublishStreamScheduler(PublishStreamService publishStreamService,
                                  ScheduledExecutorService executorService, Duration scanInterval) {
        this.publishStreamService = Objects.requireNonNull(publishStreamService, "publishStreamService");
        this.executorService = Objects.requireNonNull(executorService, "executorService");
        this.scanInterval = Objects.requireNonNull(scanInterval, "scanInterval");
    }

    /** 启动固定延迟扫描任务。 */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduledFuture = executorService.scheduleWithFixedDelay(
                this::synchronizeSafely, 0L, scanInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 停止扫描任务。 */
    @Override
    public void stop() {
        running.set(false);
        ScheduledFuture<?> future = scheduledFuture;
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 停止后续扫描，并在当前扫描任务完成后通知 Spring 生命周期处理器。
     *
     * @param callback 停止完成回调
     */
    @Override
    public void stop(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        stop();
        executorService.execute(callback);
    }

    /** @return 调度任务是否已启动 */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /** @return 自动启动调度任务 */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /** @return 在普通应用组件之后启动 */
    @Override
    public int getPhase() {
        return 0;
    }

    private void synchronizeSafely() {
        if (!running.get()) {
            return;
        }
        try {
            publishStreamService.synchronize();
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Unable to synchronize publish stream directory", exception);
        }
    }
}

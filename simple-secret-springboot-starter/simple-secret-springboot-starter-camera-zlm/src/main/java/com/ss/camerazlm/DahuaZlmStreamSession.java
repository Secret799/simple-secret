package com.ss.camerazlm;

import com.ss.ics.dahua.DahuaStreamFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 一路大华 Annex-B H.264 到 ZLM 的受控转推会话。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public final class DahuaZlmStreamSession implements AutoCloseable {

    /** 日志门面。 */
    private static final Logger log = LoggerFactory.getLogger(DahuaZlmStreamSession.class);

    /** ZLM 应用名。 */
    private final String app;
    /** ZLM 流名。 */
    private final String stream;
    /** H.264 推流管理器。 */
    private final H264StreamPublisher publisher;
    /** native 回调与推流线程之间的有界帧队列。 */
    private final ArrayBlockingQueue<byte[]> frameQueue;
    /** 单帧允许进入适配层的最大字节数。 */
    private final int maxFrameBytes;
    /** 单路排队和推送中的总字节预算。 */
    private final long maxBufferedBytes;
    /** 当前排队和推送中的总字节数。 */
    private long bufferedBytes;
    /** EasyMedia 解析器仍保留的累积字节数。 */
    private long publisherRetainedBytes;
    /** 字节预算状态锁。 */
    private final Object byteBudgetLock = new Object();
    /** 单路流顺序消费线程池。 */
    private final ThreadPoolExecutor worker;
    /** 等待消费线程退出的最大时长。 */
    private final Duration closeTimeout;
    /** 会话完全关闭后的注册表清理动作。 */
    private final Consumer<DahuaZlmStreamSession> closedAction;
    /** 首个异步失败。 */
    private final AtomicReference<RuntimeException> failure = new AtomicReference<>();
    /** 是否已经请求停止。 */
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    /** 是否由调用方主动发起关闭。 */
    private final AtomicBoolean callerClosing = new AtomicBoolean(false);
    /** 是否已经完全关闭。 */
    private final AtomicBoolean closed = new AtomicBoolean(false);
    /** 资源状态保护锁。 */
    private final Object resourceLock = new Object();

    /** 厂商实时预览会话。 */
    private AutoCloseable sourceSession;
    /** 厂商会话启动是否已经产生最终结果。 */
    private boolean sourceResolved;
    /** 厂商会话是否已经停止。 */
    private boolean sourceStopped;
    /** ZLM publisher 是否已经停止。 */
    private boolean publisherStopped;

    DahuaZlmStreamSession(
            String app, String stream, H264StreamPublisher publisher,
            int queueCapacity, int maxFrameBytes, long maxBufferedBytes,
            Duration closeTimeout,
            Consumer<DahuaZlmStreamSession> closedAction) {
        this.app = app;
        this.stream = stream;
        this.publisher = publisher;
        this.frameQueue = new ArrayBlockingQueue<>(queueCapacity);
        this.maxFrameBytes = maxFrameBytes;
        this.maxBufferedBytes = maxBufferedBytes;
        this.closeTimeout = closeTimeout;
        this.closedAction = closedAction;
        this.worker = createWorker(stream);
        this.worker.execute(this::consumeFrames);
    }

    /**
     * 接收已脱离 native 内存生命周期的帧快照。
     *
     * @param frame 大华 Annex-B H.264 帧
     */
    void accept(DahuaStreamFrame frame) {
        if (stopping.get()) {
            return;
        }
        byte[] data = frame.data();
        if (data.length > maxFrameBytes) {
            fail(new CameraZlmException("Camera stream frame exceeds byte limit"));
            return;
        }
        if (!reserveBytes(data.length)) {
            fail(new CameraZlmException("Camera stream byte budget is exhausted"));
            return;
        }
        if (!frameQueue.offer(data)) {
            releaseQueuedBytes(data.length);
            fail(new CameraZlmException("Camera stream queue is full"));
        }
    }

    /**
     * 绑定成功启动的厂商预览资源。
     *
     * @param session 厂商预览会话
     */
    void attachSource(AutoCloseable session) {
        synchronized (resourceLock) {
            sourceSession = session;
            sourceResolved = true;
        }
        if (stopping.get()) {
            finishClose(true);
        }
    }

    /** 标记厂商预览启动失败，不再等待预览句柄。 */
    void sourceStartFailed() {
        synchronized (resourceLock) {
            sourceResolved = true;
            sourceStopped = true;
        }
    }

    /**
     * 返回首个异步失败，便于调用方监控会话。
     *
     * @return 未失败时为空
     */
    public Optional<RuntimeException> failure() {
        return Optional.ofNullable(failure.get());
    }

    /**
     * 判断厂商预览和 ZLM 推流资源是否均已释放。
     *
     * @return 完全关闭时为 true
     */
    public boolean isClosed() {
        return closed.get();
    }

    /** 停止取流并按“厂商预览、ZLM publisher”顺序释放资源。 */
    @Override
    public void close() {
        callerClosing.set(true);
        stopping.set(true);
        finishClose(true);
        RuntimeException closeFailure = failure.get();
        if (!closed.get() && closeFailure != null) {
            throw closeFailure;
        }
    }

    private ThreadPoolExecutor createWorker(String streamName) {
        return new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), runnable -> {
                    Thread thread = new Thread(runnable, "camera-zlm-" + safeThreadSuffix(streamName));
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    private void consumeFrames() {
        try {
            while (!stopping.get()) {
                byte[] frame = frameQueue.take();
                boolean budgetTransferred = false;
                try {
                    int retainedBytes = publisher.push(app, stream, frame);
                    finishPublishedFrame(frame.length, retainedBytes);
                    budgetTransferred = true;
                } finally {
                    if (!budgetTransferred) {
                        releaseFailedPublish(frame.length);
                    }
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordUnexpectedInterrupt(exception);
        } catch (RuntimeException exception) {
            failure.compareAndSet(null, exception);
        } catch (LinkageError error) {
            failure.compareAndSet(null,
                    new CameraZlmException("Native ZLM publisher failed", error));
        } finally {
            stopping.set(true);
            if (!callerClosing.get()) {
                finishClose(false);
            }
        }
    }

    /**
     * 为新帧预留当前会话的堆内存预算。
     *
     * @param frameLength 新帧字节数
     * @return 预算充足时为 true
     */
    private boolean reserveBytes(int frameLength) {
        synchronized (byteBudgetLock) {
            if (bufferedBytes + publisherRetainedBytes
                    > maxBufferedBytes - frameLength) {
                return false;
            }
            bufferedBytes += frameLength;
            return true;
        }
    }

    /**
     * 将已处理帧预算转换为 publisher 累积区预算。
     *
     * @param frameLength 已处理帧字节数
     * @param retainedBytes publisher 仍保留的累积字节数
     */
    private void finishPublishedFrame(int frameLength, int retainedBytes) {
        synchronized (byteBudgetLock) {
            bufferedBytes -= frameLength;
            publisherRetainedBytes = retainedBytes;
        }
    }

    /**
     * 释放推送失败帧的预算。
     *
     * @param frameLength 推送失败帧字节数
     */
    private void releaseFailedPublish(int frameLength) {
        synchronized (byteBudgetLock) {
            if (bufferedBytes >= frameLength) {
                bufferedBytes -= frameLength;
            }
        }
    }

    /**
     * 释放未进入 publisher 的排队帧预算。
     *
     * @param frameLength 排队帧字节数
     */
    private void releaseQueuedBytes(int frameLength) {
        synchronized (byteBudgetLock) {
            bufferedBytes -= frameLength;
        }
    }

    private void fail(RuntimeException exception) {
        if (failure.compareAndSet(null, exception)) {
            stopping.set(true);
            worker.shutdownNow();
        }
    }

    private void finishClose(boolean waitForWorker) {
        RuntimeException cleanupFailure = closeSource();
        worker.shutdownNow();
        if (waitForWorker) {
            cleanupFailure = combine(cleanupFailure, awaitWorker());
        }
        if (isSourceStopped() && (!waitForWorker || worker.isTerminated())) {
            cleanupFailure = combine(cleanupFailure, closePublisher());
        }
        recordCleanupFailure(cleanupFailure);
        markClosedIfComplete();
    }

    private boolean isSourceStopped() {
        synchronized (resourceLock) {
            return sourceResolved && sourceStopped;
        }
    }

    private RuntimeException closeSource() {
        synchronized (resourceLock) {
            if (!sourceResolved || sourceStopped) {
                return null;
            }
            try {
                sourceSession.close();
                sourceStopped = true;
                return null;
            } catch (Exception exception) {
                return new CameraZlmException("Failed to stop camera stream", exception);
            } catch (LinkageError error) {
                return new CameraZlmException("Native camera stream stop failed", error);
            }
        }
    }

    private RuntimeException closePublisher() {
        synchronized (resourceLock) {
            if (publisherStopped) {
                return null;
            }
            try {
                publisher.stop(app, stream);
                publisherStopped = true;
                return null;
            } catch (RuntimeException exception) {
                return new CameraZlmException("Failed to stop ZLM stream", exception);
            } catch (LinkageError error) {
                return new CameraZlmException("Native ZLM stream stop failed", error);
            }
        }
    }

    private RuntimeException awaitWorker() {
        try {
            if (!worker.awaitTermination(closeTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return new CameraZlmException("Camera stream worker did not stop in time");
            }
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new CameraZlmException("Interrupted while stopping camera stream", exception);
        }
    }

    private void recordUnexpectedInterrupt(InterruptedException exception) {
        if (!stopping.get()) {
            failure.compareAndSet(null,
                    new CameraZlmException("Camera stream worker interrupted", exception));
        }
    }

    private void recordCleanupFailure(RuntimeException cleanupFailure) {
        if (cleanupFailure == null) {
            return;
        }
        RuntimeException primary = failure.get();
        if (primary == null && failure.compareAndSet(null, cleanupFailure)) {
            return;
        }
        primary = failure.get();
        if (primary != null && primary != cleanupFailure) {
            primary.addSuppressed(cleanupFailure);
        }
        log.warn("Camera-to-ZLM stream cleanup was incomplete", cleanupFailure);
    }

    private void markClosedIfComplete() {
        boolean resourcesClosed;
        synchronized (resourceLock) {
            resourcesClosed = sourceResolved && sourceStopped && publisherStopped;
        }
        if (resourcesClosed && closed.compareAndSet(false, true)) {
            clearQueuedFrames();
            closedAction.accept(this);
        }
    }

    private void clearQueuedFrames() {
        byte[] frame;
        while ((frame = frameQueue.poll()) != null) {
            releaseQueuedBytes(frame.length);
        }
        synchronized (byteBudgetLock) {
            publisherRetainedBytes = 0L;
        }
    }

    private static RuntimeException combine(RuntimeException first, RuntimeException second) {
        if (first == null) {
            return second;
        }
        if (second != null) {
            first.addSuppressed(second);
        }
        return first;
    }

    private static String safeThreadSuffix(String streamName) {
        return streamName.length() <= 32 ? streamName : streamName.substring(0, 32);
    }
}

package com.ss.application.pushstream.process;

import com.ss.application.pushstream.scan.MediaFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 管理每个媒体文件对应的 FFmpeg 长期运行进程。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public class FfmpegProcessManager implements ManagedStreamProcesses {

    /** 首次异常退出后的重启等待时间。 */
    private static final Duration INITIAL_RESTART_BACKOFF = Duration.ofSeconds(5);

    /** 连续异常退出时允许使用的最大重启等待时间。 */
    private static final Duration MAX_RESTART_BACKOFF = Duration.ofMinutes(5);

    /** 连续健康运行达到该时长后重置失败计数。 */
    private static final Duration STABLE_RUN_DURATION = Duration.ofMinutes(5);

    /** FFmpeg 参数工厂。 */
    private final FfmpegCommand commandFactory;

    /** 外部进程启动边界。 */
    private final ProcessLauncher processLauncher;

    /** 允许同时运行的最大进程数。 */
    private final int maxConcurrentStreams;

    /** 进程优雅退出等待时间。 */
    private final Duration shutdownTimeout;

    /** 退避时间使用的可测试时钟。 */
    private final Clock clock;

    /** 按流标识维护的运行中进程。 */
    private final Map<String, Process> runningProcesses = new LinkedHashMap<>();

    /** 按流标识保存的进程失败与下次允许启动时间。 */
    private final Map<String, ProcessFailure> failures = new LinkedHashMap<>();

    /** 按流标识保存的连续异常退出次数。 */
    private final Map<String, Integer> failureCounts = new LinkedHashMap<>();

    /** 按流标识保存的当前进程启动时间。 */
    private final Map<String, Instant> startedAt = new LinkedHashMap<>();

    /** 管理器是否已经永久关闭。 */
    private boolean closed;

    /**
     * 创建 FFmpeg 进程管理器。
     *
     * @param commandFactory FFmpeg 参数工厂
     * @param processLauncher 进程启动器
     * @param maxConcurrentStreams 最大并发进程数
     * @param shutdownTimeout 进程退出等待时间
     */
    public FfmpegProcessManager(FfmpegCommand commandFactory, ProcessLauncher processLauncher,
                                int maxConcurrentStreams, Duration shutdownTimeout) {
        this(commandFactory, processLauncher, maxConcurrentStreams, shutdownTimeout, Clock.systemUTC());
    }

    /**
     * 创建使用指定时钟的 FFmpeg 进程管理器。
     *
     * @param commandFactory FFmpeg 参数工厂
     * @param processLauncher 进程启动器
     * @param maxConcurrentStreams 最大并发进程数
     * @param shutdownTimeout 进程退出等待时间
     * @param clock 计算重启退避时间的时钟
     */
    FfmpegProcessManager(FfmpegCommand commandFactory, ProcessLauncher processLauncher,
                         int maxConcurrentStreams, Duration shutdownTimeout, Clock clock) {
        this.commandFactory = Objects.requireNonNull(commandFactory, "commandFactory");
        this.processLauncher = Objects.requireNonNull(processLauncher, "processLauncher");
        if (maxConcurrentStreams < 1) {
            throw new IllegalArgumentException("maxConcurrentStreams must be positive");
        }
        this.maxConcurrentStreams = maxConcurrentStreams;
        this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 启动一个新的受管推流进程。
     *
     * @param mediaFile 待推流媒体文件
     */
    @Override
    public synchronized void start(MediaFile mediaFile) {
        Objects.requireNonNull(mediaFile, "mediaFile");
        ensureOpen();
        removeExitedProcesses();
        ensureRestartAllowed(mediaFile.streamId());
        if (runningProcesses.containsKey(mediaFile.streamId())) {
            throw new IllegalStateException("Stream is already running: " + mediaFile.streamId());
        }
        if (runningProcesses.size() >= maxConcurrentStreams) {
            throw new IllegalStateException("The maximum concurrent FFmpeg process count has been reached");
        }
        try {
            Process process = processLauncher.launch(commandFactory.create(mediaFile));
            runningProcesses.put(mediaFile.streamId(), process);
            startedAt.put(mediaFile.streamId(), Instant.now(clock));
            failures.remove(mediaFile.streamId());
        } catch (IOException exception) {
            recordFailure(mediaFile.streamId(), "FFmpeg process launch failed; restart delayed");
            throw new FfmpegProcessException("Unable to start FFmpeg process", exception);
        }
    }

    /**
     * 停止旧进程并用当前文件快照重新启动。
     *
     * @param mediaFile 当前媒体文件快照
     */
    @Override
    public synchronized void restart(MediaFile mediaFile) {
        stop(mediaFile.streamId());
        start(mediaFile);
    }

    /**
     * 停止指定流对应的进程。
     *
     * @param streamId 流标识
     */
    @Override
    public synchronized void stop(String streamId) {
        Process process = runningProcesses.remove(streamId);
        failures.remove(streamId);
        failureCounts.remove(streamId);
        startedAt.remove(streamId);
        if (process != null) {
            destroyProcess(process);
        }
    }

    /**
     * 在一个共享停机截止时间内停止指定流。
     *
     * @param streamIds 流标识集合
     */
    @Override
    public synchronized void stopAll(Set<String> streamIds) {
        Objects.requireNonNull(streamIds, "streamIds");
        Map<String, Process> processes = removeProcesses(streamIds);
        destroyProcessesWithinDeadline(processes.values());
    }

    /**
     * 获取仍在运行的流标识快照。
     *
     * @return 不可变流标识集合
     */
    @Override
    public synchronized Set<String> runningStreamIds() {
        removeExitedProcesses();
        return Collections.unmodifiableSet(Set.copyOf(runningProcesses.keySet()));
    }

    /**
     * 获取当前进程失败的脱敏说明。
     *
     * @return 按流标识索引的不可变失败说明
     */
    @Override
    public synchronized Map<String, String> failureMessages() {
        removeExitedProcesses();
        Map<String, String> messages = new LinkedHashMap<>();
        failures.forEach((streamId, failure) -> messages.put(streamId, failure.message()));
        return Collections.unmodifiableMap(messages);
    }

    /** 停止全部受管进程。 */
    @Override
    public synchronized void close() {
        closed = true;
        destroyProcessesWithinDeadline(runningProcesses.values());
        runningProcesses.clear();
        failures.clear();
        failureCounts.clear();
        startedAt.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("FFmpeg process manager is closed");
        }
    }

    private void removeExitedProcesses() {
        runningProcesses.entrySet().removeIf(entry -> {
            Process process = entry.getValue();
            if (process.isAlive()) {
                return false;
            }
            recordUnexpectedExit(entry.getKey(), process.exitValue());
            return true;
        });
    }

    private void ensureRestartAllowed(String streamId) {
        ProcessFailure failure = failures.get(streamId);
        if (failure != null && Instant.now(clock).isBefore(failure.nextAttemptAt())) {
            throw new FfmpegProcessException("FFmpeg restart is in backoff", null);
        }
    }

    private void recordUnexpectedExit(String streamId, int exitCode) {
        Instant processStartedAt = startedAt.remove(streamId);
        if (processStartedAt != null
                && !Instant.now(clock).isBefore(processStartedAt.plus(STABLE_RUN_DURATION))) {
            failureCounts.remove(streamId);
        }
        recordFailure(streamId, "FFmpeg exited with code " + exitCode + "; restart delayed");
    }

    private void recordFailure(String streamId, String message) {
        int failureCount = Math.min(failureCounts.getOrDefault(streamId, 0) + 1, 7);
        failureCounts.put(streamId, failureCount);
        Duration delay = INITIAL_RESTART_BACKOFF.multipliedBy(1L << (failureCount - 1));
        if (delay.compareTo(MAX_RESTART_BACKOFF) > 0) {
            delay = MAX_RESTART_BACKOFF;
        }
        failures.put(streamId, new ProcessFailure(
                Instant.now(clock).plus(delay),
                message));
    }

    private Map<String, Process> removeProcesses(Set<String> streamIds) {
        Map<String, Process> processes = new LinkedHashMap<>();
        for (String streamId : streamIds) {
            Process process = runningProcesses.remove(streamId);
            failures.remove(streamId);
            failureCounts.remove(streamId);
            startedAt.remove(streamId);
            if (process != null) {
                processes.put(streamId, process);
            }
        }
        return processes;
    }

    private void destroyProcessesWithinDeadline(Iterable<Process> processes) {
        java.util.List<Process> aliveProcesses = new java.util.ArrayList<>();
        for (Process process : processes) {
            if (process.isAlive()) {
                process.destroy();
                aliveProcesses.add(process);
            }
        }
        long deadline = System.nanoTime() + shutdownTimeout.toNanos();
        for (Process process : aliveProcesses) {
            waitUntilDeadline(process, deadline);
        }
    }

    private void waitUntilDeadline(Process process, long deadline) {
        long remainingNanos = Math.max(0L, deadline - System.nanoTime());
        try {
            boolean exited = process.waitFor(remainingNanos, TimeUnit.NANOSECONDS);
            if (!exited && process.isAlive()) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void destroyProcess(Process process) {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            boolean exited = process.waitFor(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited && process.isAlive()) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private record ProcessFailure(Instant nextAttemptAt, String message) {
    }
}

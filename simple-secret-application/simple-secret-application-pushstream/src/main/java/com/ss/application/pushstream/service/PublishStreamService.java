package com.ss.application.pushstream.service;

import com.ss.application.pushstream.process.ManagedStreamProcesses;
import com.ss.application.pushstream.scan.MediaFile;
import com.ss.application.pushstream.scan.MediaFileChangeSet;
import com.ss.application.pushstream.scan.MediaFileScanner;
import com.ss.application.pushstream.status.PublishStreamState;
import com.ss.application.pushstream.status.PublishStreamStatus;
import com.ss.application.pushstream.status.PublishStreamStatusView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 扫描媒体目录并协调 FFmpeg 进程和 ZLMediaKit 状态。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public class PublishStreamService {

    /** 应用日志记录器。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(PublishStreamService.class);

    /** 媒体文件扫描器。 */
    private final MediaFileScanner scanner;

    /** 受管 FFmpeg 进程。 */
    private final ManagedStreamProcesses processes;

    /** 媒体服务器查询边界。 */
    private final MediaServerClient mediaServerClient;

    /** ZLMediaKit 应用名。 */
    private final String app;

    /** 可测试时钟。 */
    private final Clock clock;

    /** 上一次扫描文件快照。 */
    private List<MediaFile> previousFiles = List.of();

    /** 按流标识保存的最近错误说明。 */
    private final Map<String, String> errors = new LinkedHashMap<>();

    /**
     * 创建推流应用服务。
     *
     * @param scanner 媒体文件扫描器
     * @param processes 受管 FFmpeg 进程
     * @param mediaServerClient 媒体服务器查询边界
     * @param app ZLMediaKit 应用名
     * @param clock 可测试时钟
     */
    public PublishStreamService(MediaFileScanner scanner, ManagedStreamProcesses processes,
                                MediaServerClient mediaServerClient, String app, Clock clock) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.mediaServerClient = Objects.requireNonNull(mediaServerClient, "mediaServerClient");
        this.app = Objects.requireNonNull(app, "app");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 执行一次目录扫描并应用文件变化。
     *
     * @throws IOException 扫描根目录失败时抛出
     */
    public synchronized void synchronize() throws IOException {
        List<MediaFile> currentFiles = scanner.scan();
        MediaFileChangeSet changes = MediaFileChangeSet.between(previousFiles, currentFiles);
        Set<String> streamsToStop = new HashSet<>();
        for (MediaFile removedFile : changes.removed()) {
            streamsToStop.add(removedFile.streamId());
            errors.remove(removedFile.streamId());
        }
        changes.updated().forEach(file -> streamsToStop.add(file.streamId()));
        processes.stopAll(streamsToStop);
        for (MediaFile updatedFile : changes.updated()) {
            runSafely(updatedFile);
        }
        for (MediaFile addedFile : changes.added()) {
            runSafely(addedFile);
        }
        recoverMissingProcesses(currentFiles, changes);
        previousFiles = List.copyOf(currentFiles);
    }

    /**
     * 获取当前状态快照。
     *
     * @return 不包含本地绝对路径的状态视图
     */
    public synchronized PublishStreamStatusView status() {
        Set<String> runningStreamIds = processes.runningStreamIds();
        MediaServerQueryResult mediaServerResult = queryOnlineStreamIds();
        Map<String, String> processFailures = processes.failureMessages();
        List<PublishStreamStatus> statuses = new ArrayList<>(previousFiles.size());
        for (MediaFile mediaFile : previousFiles) {
            String errorMessage = processFailures.getOrDefault(
                    mediaFile.streamId(), errors.get(mediaFile.streamId()));
            PublishStreamState state = resolveState(
                    mediaFile.streamId(), runningStreamIds,
                    mediaServerResult.onlineStreamIds(), errorMessage);
            statuses.add(new PublishStreamStatus(mediaFile.fileName(), mediaFile.streamId(), state,
                    mediaFile.size(), mediaFile.lastModifiedTime(), errorMessage));
        }
        return new PublishStreamStatusView(
                Instant.now(clock), statuses, mediaServerResult.reachable());
    }

    private void runSafely(MediaFile mediaFile) {
        try {
            scanner.verifyReadable(mediaFile);
            processes.start(mediaFile);
            errors.remove(mediaFile.streamId());
        } catch (IOException | RuntimeException exception) {
            errors.put(mediaFile.streamId(), "FFmpeg process could not be started");
            LOGGER.warn("Unable to start media stream fileName={}, streamId={}",
                    mediaFile.fileName(), mediaFile.streamId(), exception);
        }
    }

    private void recoverMissingProcesses(List<MediaFile> currentFiles, MediaFileChangeSet changes) {
        Set<String> runningStreamIds = processes.runningStreamIds();
        Set<String> changedStreamIds = new HashSet<>();
        changes.added().forEach(file -> changedStreamIds.add(file.streamId()));
        changes.updated().forEach(file -> changedStreamIds.add(file.streamId()));
        for (MediaFile mediaFile : currentFiles) {
            if (changedStreamIds.contains(mediaFile.streamId())
                    || runningStreamIds.contains(mediaFile.streamId())) {
                continue;
            }
            runSafely(mediaFile);
            runningStreamIds = processes.runningStreamIds();
        }
    }

    private MediaServerQueryResult queryOnlineStreamIds() {
        try {
            return new MediaServerQueryResult(mediaServerClient.onlineStreamIds(app), true);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to query ZLMediaKit stream status for app={}", app, exception);
            return new MediaServerQueryResult(Set.of(), false);
        }
    }

    private PublishStreamState resolveState(String streamId, Set<String> runningStreamIds,
                                            Set<String> onlineStreamIds, String errorMessage) {
        if (errorMessage != null) {
            return PublishStreamState.FAILED;
        }
        if (!runningStreamIds.contains(streamId)) {
            return PublishStreamState.STOPPED;
        }
        return onlineStreamIds.contains(streamId)
                ? PublishStreamState.ONLINE : PublishStreamState.STARTING;
    }

    private record MediaServerQueryResult(Set<String> onlineStreamIds, boolean reachable) {
    }
}

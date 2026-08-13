package com.ss.application.pushstream.process;

import com.ss.application.pushstream.scan.MediaFile;

import java.util.Set;
import java.util.Map;

/**
 * 受管媒体推流进程契约。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public interface ManagedStreamProcesses extends AutoCloseable {

    /**
     * 启动媒体文件推流。
     *
     * @param mediaFile 媒体文件快照
     */
    void start(MediaFile mediaFile);

    /**
     * 重启媒体文件推流。
     *
     * @param mediaFile 当前媒体文件快照
     */
    void restart(MediaFile mediaFile);

    /**
     * 停止指定流。
     *
     * @param streamId 流标识
     */
    void stop(String streamId);

    /**
     * 使用实现提供的共享停机边界停止多路流。
     *
     * @param streamIds 流标识集合
     */
    default void stopAll(Set<String> streamIds) {
        streamIds.forEach(this::stop);
    }

    /**
     * 获取仍在运行的流标识。
     *
     * @return 不可变流标识集合
     */
    Set<String> runningStreamIds();

    /**
     * 获取不包含本地路径和外部进程原始输出的失败快照。
     *
     * @return 按流标识索引的失败说明
     */
    default Map<String, String> failureMessages() {
        return Map.of();
    }

    /** 停止全部受管进程。 */
    @Override
    void close();
}

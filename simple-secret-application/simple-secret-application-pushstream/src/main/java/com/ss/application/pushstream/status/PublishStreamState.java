package com.ss.application.pushstream.status;

/**
 * 单个媒体文件的推流状态。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public enum PublishStreamState {
    /** 本地 FFmpeg 进程和 ZLMediaKit 流均在线。 */
    ONLINE,
    /** 本地 FFmpeg 进程已运行，但 ZLMediaKit 尚未报告在线。 */
    STARTING,
    /** 本地 FFmpeg 进程未运行。 */
    STOPPED,
    /** 最近一次启动或重启失败。 */
    FAILED
}

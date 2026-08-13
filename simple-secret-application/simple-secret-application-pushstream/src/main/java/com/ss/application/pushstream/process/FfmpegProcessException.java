package com.ss.application.pushstream.process;

/**
 * FFmpeg 外部进程启动或管理失败异常。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public class FfmpegProcessException extends RuntimeException {

    /**
     * 创建 FFmpeg 进程异常。
     *
     * @param message 稳定错误说明
     * @param cause 原始异常
     */
    public FfmpegProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}

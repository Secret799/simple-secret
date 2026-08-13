package com.ss.application.pushstream.scan;

/**
 * 扫描媒体文件元数据失败异常。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public class MediaFileScanException extends RuntimeException {

    /**
     * 创建扫描异常。
     *
     * @param message 稳定错误说明
     * @param cause 原始异常
     */
    public MediaFileScanException(String message, Throwable cause) {
        super(message, cause);
    }
}

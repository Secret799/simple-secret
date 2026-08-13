package com.ss.ics.dahua;

/**
 * 大华原生 SDK 操作失败。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public final class DahuaSdkException extends RuntimeException {
    /** 厂商 SDK 错误码。 */
    private final int errorCode;

    /**
     * 创建大华 SDK 异常。
     *
     * @param message 错误描述
     * @param errorCode 厂商 SDK 错误码
     */
    public DahuaSdkException(String message, int errorCode) {
        super(message + " (code=" + errorCode + ")");
        this.errorCode = errorCode;
    }

    /**
     * 创建保留原始原因的大华 SDK 异常。
     *
     * @param message 错误描述
     * @param errorCode 厂商 SDK 错误码
     * @param cause 原始异常
     */
    public DahuaSdkException(String message, int errorCode, Throwable cause) {
        super(message + " (code=" + errorCode + ")", cause);
        this.errorCode = errorCode;
    }

    /**
     * 获取厂商 SDK 错误码。
     *
     * @return 厂商 SDK 错误码
     */
    public int errorCode() {
        return errorCode;
    }
}

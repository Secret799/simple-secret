package com.ss.ics.hikvision;

/**
 * 海康原生 SDK 操作失败时抛出的异常。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public class HikvisionSdkException extends RuntimeException {
    /** 不包含设备凭据的操作说明。 */
    private final String operation;

    /** 厂商 SDK 错误码。 */
    private final int errorCode;

    /**
     * 创建海康 SDK 异常。
     *
     * @param operation 不包含设备凭据的操作说明
     * @param errorCode 厂商 SDK 错误码
     */
    public HikvisionSdkException(String operation, int errorCode) {
        super(operation + " (code=" + errorCode + ")");
        this.operation = operation;
        this.errorCode = errorCode;
    }

    /**
     * 创建保留原始原因的海康 SDK 异常。
     *
     * @param operation 不包含设备凭据的操作说明
     * @param errorCode 厂商 SDK 错误码
     * @param cause 原始异常
     */
    public HikvisionSdkException(
            String operation, int errorCode, Throwable cause) {
        super(operation + " (code=" + errorCode + ")", cause);
        this.operation = operation;
        this.errorCode = errorCode;
    }

    /**
     * 获取操作说明。
     *
     * @return 操作说明
     */
    public String getOperation() {
        return operation;
    }

    /**
     * 获取厂商 SDK 错误码。
     *
     * @return 厂商 SDK 错误码
     */
    public int getErrorCode() {
        return errorCode;
    }
}

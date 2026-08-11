package com.ss.ics.dahua;

/** 大华原生 SDK 操作失败。 */
public final class DahuaSdkException extends RuntimeException {
    private final int errorCode;

    DahuaSdkException(String message, int errorCode) {
        super(message + " (code=" + errorCode + ")");
        this.errorCode = errorCode;
    }

    /** @return 厂商 SDK 错误码 */
    public int errorCode() {
        return errorCode;
    }
}

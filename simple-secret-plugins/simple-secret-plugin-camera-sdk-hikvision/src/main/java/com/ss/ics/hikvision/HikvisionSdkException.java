package com.ss.ics.hikvision;

/** 海康原生 SDK 操作失败时抛出的异常。 */
public class HikvisionSdkException extends RuntimeException {
    private final String operation;
    private final int errorCode;

    /**
     * @param operation 不包含设备凭据的操作说明
     * @param errorCode 厂商 SDK 错误码
     */
    public HikvisionSdkException(String operation, int errorCode) {
        super(operation + " (code=" + errorCode + ")");
        this.operation = operation;
        this.errorCode = errorCode;
    }

    /** @return 操作说明 */
    public String getOperation() {
        return operation;
    }

    /** @return 厂商 SDK 错误码 */
    public int getErrorCode() {
        return errorCode;
    }
}

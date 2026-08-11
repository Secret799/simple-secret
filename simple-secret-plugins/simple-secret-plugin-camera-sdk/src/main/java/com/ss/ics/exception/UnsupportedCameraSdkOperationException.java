package com.ss.ics.exception;

/** 厂商驱动不支持所请求能力时抛出的异常。 */
public class UnsupportedCameraSdkOperationException extends RuntimeException {

    /**
     * @param message 不包含设备凭据的错误说明
     */
    public UnsupportedCameraSdkOperationException(String message) {
        super(message);
    }
}

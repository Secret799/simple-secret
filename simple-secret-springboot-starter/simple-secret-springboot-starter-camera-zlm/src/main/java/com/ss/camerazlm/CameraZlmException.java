package com.ss.camerazlm;

/**
 * 摄像机码流转推 ZLM 失败。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public class CameraZlmException extends RuntimeException {

    /**
     * 创建业务异常。
     *
     * @param message 不含设备凭据的错误信息
     */
    public CameraZlmException(String message) {
        super(message);
    }

    /**
     * 创建保留原始原因的业务异常。
     *
     * @param message 不含设备凭据的错误信息
     * @param cause 原始异常
     */
    public CameraZlmException(String message, Throwable cause) {
        super(message, cause);
    }
}

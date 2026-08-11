package com.ss.encrypt.web;

/** 默认 failure handler 可映射的 API 加密失败类型。 */
public enum ApiEncryptionFailureReason {
    MISSING_KEY_HEADER(400),
    INVALID_REQUEST_PAYLOAD(400),
    PAYLOAD_TOO_LARGE(413),
    RESPONSE_ENCRYPTION_FAILED(500);

    private final int status;

    ApiEncryptionFailureReason(int status) {
        this.status = status;
    }

    /** 返回建议的 HTTP 状态码。 */
    public int status() {
        return status;
    }
}

package com.ss.encrypt.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** 只返回状态码且不暴露密码异常细节的默认 failure handler。 */
public final class DefaultApiEncryptionFailureHandler
        implements ApiEncryptionFailureHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            ApiEncryptionFailureReason reason) throws IOException {
        if (!response.isCommitted()) {
            response.resetBuffer();
            response.sendError(reason.status());
        }
    }
}

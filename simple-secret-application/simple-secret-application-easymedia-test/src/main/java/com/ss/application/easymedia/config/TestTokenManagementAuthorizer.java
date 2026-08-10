package com.ss.application.easymedia.config;

import com.ss.easymedia.security.EasyMediaManagementAuthorizer;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 使用固定请求头令牌保护本地 EasyMedia 管理接口。
 */
public final class TestTokenManagementAuthorizer implements EasyMediaManagementAuthorizer {

    /** 测试管理接口使用的令牌请求头。 */
    public static final String TOKEN_HEADER = "X-Test-Token";

    private final byte[] expectedToken;

    /**
     * 创建测试授权器。
     *
     * @param expectedToken 预期令牌
     */
    public TestTokenManagementAuthorizer(String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalArgumentException("EasyMedia test management token must not be blank");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean isAuthorized(HttpServletRequest request) {
        String actualToken = request.getHeader(TOKEN_HEADER);
        return actualToken != null && MessageDigest.isEqual(
                expectedToken, actualToken.getBytes(StandardCharsets.UTF_8));
    }
}

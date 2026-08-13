package com.ss.application.djisei.parser;

import java.util.Objects;

/**
 * SEI 媒体数据解析问题。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public final class SeiParseIssue {

    /** 稳定且有界的问题代码。 */
    private final String code;

    /** 稳定且有界的问题说明。 */
    private final String message;

    /**
     * 创建解析问题。
     *
     * @param code 稳定问题代码
     * @param message 稳定问题说明
     */
    public SeiParseIssue(String code, String message) {
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * 获取问题代码。
     *
     * @return 问题代码
     */
    public String code() {
        return code;
    }

    /**
     * 获取问题说明。
     *
     * @return 问题说明
     */
    public String message() {
        return message;
    }
}

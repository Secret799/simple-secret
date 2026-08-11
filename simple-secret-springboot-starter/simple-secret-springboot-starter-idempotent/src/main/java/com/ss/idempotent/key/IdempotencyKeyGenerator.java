package com.ss.idempotent.key;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Method;

/** 为一次受保护的方法调用生成存储 key。 */
@FunctionalInterface
public interface IdempotencyKeyGenerator {

    /**
     * 生成幂等 key。
     *
     * @param method 被调用方法
     * @param args 方法参数
     * @param request 当前 HTTP 请求
     * @return 不包含身份和参数原文的存储 key
     */
    String generate(Method method, Object[] args, HttpServletRequest request);
}

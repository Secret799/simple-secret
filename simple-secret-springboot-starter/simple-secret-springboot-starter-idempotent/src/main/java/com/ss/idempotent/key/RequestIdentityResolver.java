package com.ss.idempotent.key;

import jakarta.servlet.http.HttpServletRequest;

/** 从 Servlet 请求中解析幂等隔离身份。 */
@FunctionalInterface
public interface RequestIdentityResolver {

    /**
     * 解析请求身份。
     *
     * @param request 当前 HTTP 请求
     * @return 非空身份字符串
     */
    String resolve(HttpServletRequest request);
}

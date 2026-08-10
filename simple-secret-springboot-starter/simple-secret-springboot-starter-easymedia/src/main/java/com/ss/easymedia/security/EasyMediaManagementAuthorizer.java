package com.ss.easymedia.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * EasyMedia 管理接口授权器，由宿主应用按自身认证体系实现。
 */
@FunctionalInterface
public interface EasyMediaManagementAuthorizer {

    /**
     * 判断当前请求是否允许访问管理接口。
     *
     * @param request 当前 HTTP 请求
     * @return 允许访问时返回 {@code true}
     */
    boolean isAuthorized(HttpServletRequest request);
}

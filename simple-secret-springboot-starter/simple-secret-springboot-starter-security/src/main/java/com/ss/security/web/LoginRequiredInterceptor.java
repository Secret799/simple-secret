package com.ss.security.web;

import cn.dev33.satoken.stp.StpLogic;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/** 使用 Sa-Token 校验当前请求登录状态的 WebMVC 拦截器。 */
public final class LoginRequiredInterceptor implements HandlerInterceptor {
    private final StpLogic stpLogic;

    /**
     * 创建绑定指定 Sa-Token 逻辑实例的登录拦截器。
     *
     * @param stpLogic Sa-Token 登录逻辑
     */
    public LoginRequiredInterceptor(StpLogic stpLogic) {
        this.stpLogic = Objects.requireNonNull(stpLogic, "stpLogic");
    }

    /**
     * 校验当前请求已经登录。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param handler 请求处理器
     * @return 登录校验通过时固定返回 {@code true}
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        stpLogic.checkLogin();
        return true;
    }
}

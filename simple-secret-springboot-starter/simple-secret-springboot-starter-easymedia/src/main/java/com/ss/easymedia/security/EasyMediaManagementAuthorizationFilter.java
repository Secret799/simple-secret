package com.ss.easymedia.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在请求进入 EasyMedia 管理控制器前执行统一授权。
 */
public class EasyMediaManagementAuthorizationFilter extends OncePerRequestFilter {

    private final EasyMediaManagementAuthorizer authorizer;

    public EasyMediaManagementAuthorizationFilter(EasyMediaManagementAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!authorizer.isAuthorized(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        filterChain.doFilter(request, response);
    }
}

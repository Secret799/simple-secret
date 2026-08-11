package com.ss.security.web;

import com.ss.security.config.SecurityProperties;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Objects;

/** 将登录保护拦截器注册到指定 WebMVC 路径。 */
public final class SecurityWebMvcConfigurer implements WebMvcConfigurer {
    private final LoginRequiredInterceptor interceptor;
    private final List<String> pathPatterns;
    private final List<String> excludePathPatterns;
    private final int order;

    /**
     * 使用当前配置快照创建 WebMVC 配置器。
     *
     * @param interceptor 登录保护拦截器
     * @param properties Security 配置
     */
    public SecurityWebMvcConfigurer(
            LoginRequiredInterceptor interceptor, SecurityProperties properties) {
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor");
        SecurityProperties requiredProperties = Objects.requireNonNull(properties, "properties");
        this.pathPatterns = List.copyOf(requiredProperties.getPathPatterns());
        this.excludePathPatterns = List.copyOf(requiredProperties.getExcludePathPatterns());
        this.order = requiredProperties.getOrder();
    }

    /**
     * 注册登录保护路径、排除路径和拦截器顺序。
     *
     * @param registry WebMVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (pathPatterns.isEmpty()) {
            return;
        }
        InterceptorRegistration registration = registry.addInterceptor(interceptor)
                .addPathPatterns(pathPatterns)
                .order(order);
        if (!excludePathPatterns.isEmpty()) {
            registration.excludePathPatterns(excludePathPatterns);
        }
    }
}

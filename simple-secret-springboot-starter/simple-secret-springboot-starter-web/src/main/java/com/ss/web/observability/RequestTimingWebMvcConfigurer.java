package com.ss.web.observability;

import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 将请求耗时拦截器注册到全部 WebMVC 路由。 */
public final class RequestTimingWebMvcConfigurer implements WebMvcConfigurer {

    private final RequestTimingInterceptor interceptor;

    /** 使用指定的请求耗时拦截器创建配置器。 */
    public RequestTimingWebMvcConfigurer(RequestTimingInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}

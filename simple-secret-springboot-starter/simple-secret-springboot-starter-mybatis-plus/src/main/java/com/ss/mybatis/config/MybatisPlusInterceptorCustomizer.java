package com.ss.mybatis.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;

/** 在 Simple Secret 内置插件之前定制 MyBatis-Plus 拦截器。 */
@FunctionalInterface
public interface MybatisPlusInterceptorCustomizer {

    /**
     * 向共享的 MyBatis-Plus 拦截器容器添加或配置插件。
     *
     * @param interceptor MyBatis-Plus 拦截器容器
     */
    void customize(MybatisPlusInterceptor interceptor);
}

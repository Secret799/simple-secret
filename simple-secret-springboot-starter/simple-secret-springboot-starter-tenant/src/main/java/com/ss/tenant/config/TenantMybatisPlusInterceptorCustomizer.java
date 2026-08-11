package com.ss.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ss.mybatis.config.MybatisPlusInterceptorCustomizer;
import org.springframework.core.Ordered;

import java.util.Objects;

/** 将租户拦截器放到 Simple Secret 内置 MyBatis-Plus 插件之前。 */
public final class TenantMybatisPlusInterceptorCustomizer
        implements MybatisPlusInterceptorCustomizer, Ordered {
    private final TenantLineInnerInterceptor tenantInterceptor;

    /**
     * 创建租户插件 customizer。
     *
     * @param tenantInterceptor 租户拦截器
     */
    public TenantMybatisPlusInterceptorCustomizer(
            TenantLineInnerInterceptor tenantInterceptor) {
        this.tenantInterceptor = Objects.requireNonNull(
                tenantInterceptor, "tenantInterceptor must not be null");
    }

    /**
     * 添加租户拦截器。
     *
     * @param interceptor MyBatis-Plus 拦截器容器
     */
    @Override
    public void customize(MybatisPlusInterceptor interceptor) {
        interceptor.addInnerInterceptor(tenantInterceptor);
    }

    /**
     * 返回最高优先级，确保租户条件早于分页等 SQL 改写。
     *
     * @return Spring 最高优先级
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

package com.ss.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ss.tenant.interceptor.SimpleSecretTenantLineInnerInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.List;

/** 启动完成前验证最终生效的 MyBatis-Plus 租户隔离链。 */
final class TenantIsolationVerifier implements SmartInitializingSingleton {
    private final ObjectProvider<MybatisPlusInterceptor> interceptors;
    private final TenantLineInnerInterceptor tenantInterceptor;

    TenantIsolationVerifier(
            ObjectProvider<MybatisPlusInterceptor> interceptors,
            TenantLineInnerInterceptor tenantInterceptor) {
        this.interceptors = interceptors;
        this.tenantInterceptor = tenantInterceptor;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<MybatisPlusInterceptor> containers = interceptors.stream().toList();
        if (containers.size() != 1) {
            throw new IllegalStateException(
                    "Tenant isolation requires exactly one MybatisPlusInterceptor");
        }

        List<InnerInterceptor> configured = containers.get(0).getInterceptors();
        int tenantIndex = -1;
        int tenantCount = 0;
        for (int index = 0; index < configured.size(); index++) {
            if (configured.get(index) == tenantInterceptor) {
                tenantIndex = index;
                tenantCount++;
            }
        }
        if (tenantCount != 1) {
            throw new IllegalStateException(
                    "MybatisPlusInterceptor must contain the configured tenant interceptor exactly once");
        }
        if (!(tenantInterceptor instanceof SimpleSecretTenantLineInnerInterceptor)) {
            throw new IllegalStateException(
                    "Tenant interceptor must enforce protected tenant writes");
        }

        for (int index = 0; index < tenantIndex; index++) {
            InnerInterceptor interceptor = configured.get(index);
            if (interceptor instanceof PaginationInnerInterceptor
                    || interceptor instanceof OptimisticLockerInnerInterceptor) {
                throw new IllegalStateException(
                        "Tenant interceptor must run before pagination and optimistic locking");
            }
        }
    }
}

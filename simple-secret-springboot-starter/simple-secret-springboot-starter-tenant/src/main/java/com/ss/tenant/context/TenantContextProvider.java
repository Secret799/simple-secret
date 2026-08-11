package com.ss.tenant.context;

/** 为 SQL 租户隔离提供当前租户标识。 */
@FunctionalInterface
public interface TenantContextProvider {

    /**
     * 返回当前调用上下文的租户标识。
     *
     * @return 租户标识；无法确定时可返回 {@code null}
     */
    String currentTenantId();

    /**
     * 创建不提供租户标识的 provider。
     *
     * @return 空租户 provider
     */
    static TenantContextProvider empty() {
        return () -> null;
    }
}

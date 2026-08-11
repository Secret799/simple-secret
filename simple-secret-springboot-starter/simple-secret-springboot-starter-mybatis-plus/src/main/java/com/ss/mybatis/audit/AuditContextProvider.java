package com.ss.mybatis.audit;

/** 由业务系统提供当前审计主体，基础 starter 不依赖任何认证实现。 */
@FunctionalInterface
public interface AuditContextProvider {

    /**
     * 返回当前操作的审计上下文。
     *
     * @return 审计上下文，不应返回 {@code null}
     */
    AuditContext current();

    /**
     * 返回始终为空的默认 provider。
     *
     * @return 空审计 provider
     */
    static AuditContextProvider empty() {
        return AuditContext::empty;
    }
}

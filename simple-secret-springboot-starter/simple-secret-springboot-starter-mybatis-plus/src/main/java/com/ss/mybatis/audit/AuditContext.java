package com.ss.mybatis.audit;

/**
 * 当前持久化操作的审计主体。
 *
 * @param actorId 操作人标识，可为空
 * @param departmentId 操作人所属部门标识，可为空
 */
public record AuditContext(Long actorId, Long departmentId) {
    private static final AuditContext EMPTY = new AuditContext(null, null);

    /**
     * 返回不包含主体信息的上下文。
     *
     * @return 空上下文
     */
    public static AuditContext empty() {
        return EMPTY;
    }
}

package com.ss.tenant.interceptor;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ss.tenant.exception.TenantException;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.List;
import java.util.Objects;

/** 对租户查询隔离和租户列写入同时采用失败关闭策略。 */
public final class SimpleSecretTenantLineInnerInterceptor
        extends TenantLineInnerInterceptor {
    private final TenantLineHandler tenantLineHandler;

    /**
     * 创建严格租户拦截器。
     *
     * @param tenantLineHandler 租户 SQL handler
     */
    public SimpleSecretTenantLineInnerInterceptor(
            TenantLineHandler tenantLineHandler) {
        super(Objects.requireNonNull(
                tenantLineHandler, "tenantLineHandler must not be null"));
        this.tenantLineHandler = tenantLineHandler;
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        if (!tenantLineHandler.ignoreTable(insert.getTable().getName())
                && (insert.getColumns() == null || insert.getColumns().isEmpty())) {
            throw new TenantException(
                    "Tenant-protected INSERT must declare a column list");
        }
        super.processInsert(insert, index, sql, obj);
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        if (!tenantLineHandler.ignoreTable(update.getTable().getName())
                && updatesTenantColumn(update.getUpdateSets())) {
            throw new TenantException(
                    "Explicit tenant column is not allowed in UPDATE: "
                            + tenantLineHandler.getTenantIdColumn());
        }
        super.processUpdate(update, index, sql, obj);
    }

    private boolean updatesTenantColumn(List<UpdateSet> updateSets) {
        if (updateSets == null) {
            return false;
        }
        String tenantIdColumn = tenantLineHandler.getTenantIdColumn();
        return updateSets.stream()
                .flatMap(updateSet -> updateSet.getColumns().stream())
                .map(Column::getColumnName)
                .anyMatch(column -> column.equalsIgnoreCase(tenantIdColumn));
    }
}

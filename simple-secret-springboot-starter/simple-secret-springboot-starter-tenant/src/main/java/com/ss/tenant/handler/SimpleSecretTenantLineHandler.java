package com.ss.tenant.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.ss.tenant.config.TenantProperties;
import com.ss.tenant.context.TenantContext;
import com.ss.tenant.exception.TenantException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.schema.Column;

import java.util.List;
import java.util.Objects;

/** 将当前租户安全地转换为 MyBatis-Plus SQL 租户条件。 */
public final class SimpleSecretTenantLineHandler implements TenantLineHandler {
    private final TenantContext tenantContext;
    private final TenantProperties properties;

    /**
     * 创建租户 SQL handler。
     *
     * @param tenantContext 租户上下文
     * @param properties 租户配置
     */
    public SimpleSecretTenantLineHandler(
            TenantContext tenantContext,
            TenantProperties properties) {
        this.tenantContext = Objects.requireNonNull(
                tenantContext, "tenantContext must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * 返回当前租户 SQL 字符串表达式。
     *
     * @return 租户表达式
     */
    @Override
    public Expression getTenantId() {
        return new StringValue(tenantContext.requireTenantId());
    }

    /**
     * 返回租户列名。
     *
     * @return 配置的安全列名
     */
    @Override
    public String getTenantIdColumn() {
        return properties.getColumn();
    }

    /**
     * 仅在显式作用域或排除表配置下忽略租户条件。
     *
     * @param tableName 表名
     * @return 是否忽略租户条件
     */
    @Override
    public boolean ignoreTable(String tableName) {
        return tenantContext.isIgnored() || properties.isExcludedTable(tableName);
    }

    /**
     * 拒绝由业务 SQL 显式提供租户列，避免伪造其他租户数据。
     *
     * @param columns INSERT 列清单
     * @param tenantIdColumn 租户列名
     * @return 始终为 {@code false}，由 MyBatis-Plus 注入当前租户
     * @throws TenantException INSERT 显式包含租户列
     */
    @Override
    public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
        boolean containsTenantColumn = columns.stream()
                .map(Column::getColumnName)
                .anyMatch(column -> column.equalsIgnoreCase(tenantIdColumn));
        if (containsTenantColumn) {
            throw new TenantException(
                    "Explicit tenant column is not allowed in INSERT: " + tenantIdColumn);
        }
        return false;
    }
}

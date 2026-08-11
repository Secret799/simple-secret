package com.ss.tenant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Simple Secret SQL 多租户配置。 */
@ConfigurationProperties("simple-secret.tenant")
public final class TenantProperties {
    private static final Pattern SQL_IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private boolean enabled = true;
    private String column = "tenant_id";
    private Set<String> excludedTables = Collections.emptySet();

    /**
     * 返回是否启用 SQL 租户隔离。
     *
     * @return 启用状态
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 SQL 租户隔离。
     *
     * @param enabled 启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回租户列名。
     *
     * @return 安全 SQL 标识符
     */
    public String getColumn() {
        return column;
    }

    /**
     * 设置租户列名。
     *
     * @param column 普通 SQL 标识符
     */
    public void setColumn(String column) {
        String normalized = column == null ? "" : column.trim();
        if (!SQL_IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "column must be a plain SQL identifier");
        }
        this.column = normalized;
    }

    /**
     * 返回无需租户条件的表名集合。
     *
     * @return 不可修改的小写表名集合
     */
    public Set<String> getExcludedTables() {
        return excludedTables;
    }

    /**
     * 设置无需租户条件的表名集合。
     *
     * @param excludedTables 排除表
     */
    public void setExcludedTables(Set<String> excludedTables) {
        if (excludedTables == null || excludedTables.isEmpty()) {
            this.excludedTables = Collections.emptySet();
            return;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String table : excludedTables) {
            String name = normalizeTable(table);
            if (!name.isEmpty()) {
                normalized.add(name);
            }
        }
        this.excludedTables = Collections.unmodifiableSet(normalized);
    }

    /**
     * 判断表是否显式排除租户条件。
     *
     * @param tableName SQL 表名
     * @return 表被排除时为 {@code true}
     */
    public boolean isExcludedTable(String tableName) {
        return excludedTables.contains(normalizeTable(tableName));
    }

    private static String normalizeTable(String tableName) {
        return tableName == null ? "" : tableName.trim().toLowerCase(Locale.ROOT);
    }
}

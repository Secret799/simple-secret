package com.ss.tenant.handler;

import com.ss.tenant.config.TenantProperties;
import com.ss.tenant.context.TenantContext;
import com.ss.tenant.context.TenantScope;
import com.ss.tenant.exception.TenantException;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.schema.Column;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 SQL 租户 handler 默认失败关闭且只忽略显式范围。 */
class SimpleSecretTenantLineHandlerTest {

    @Test
    void shouldReturnConfiguredColumnAndCurrentTenantExpression() {
        TenantProperties properties = new TenantProperties();
        properties.setColumn("account_id");
        TenantContext context = new TenantContext(() -> "tenant-a");
        SimpleSecretTenantLineHandler handler =
                new SimpleSecretTenantLineHandler(context, properties);

        assertThat(handler.getTenantIdColumn()).isEqualTo("account_id");
        assertThat(handler.getTenantId()).isInstanceOfSatisfying(StringValue.class,
                value -> assertThat(value.getValue()).isEqualTo("tenant-a"));
    }

    @Test
    void shouldIgnoreOnlyConfiguredTablesOrExplicitScope() {
        TenantProperties properties = new TenantProperties();
        properties.setExcludedTables(Set.of("audit_log"));
        TenantContext context = new TenantContext(() -> "tenant-a");
        SimpleSecretTenantLineHandler handler =
                new SimpleSecretTenantLineHandler(context, properties);

        assertThat(handler.ignoreTable("AUDIT_LOG")).isTrue();
        assertThat(handler.ignoreTable("orders")).isFalse();
        try (TenantScope ignored = context.ignoreTenant()) {
            assertThat(handler.ignoreTable("orders")).isTrue();
        }
        assertThat(handler.ignoreTable("orders")).isFalse();
    }

    @Test
    void shouldNotIgnoreTableWhenTenantIsMissing() {
        TenantContext context = new TenantContext(() -> null);
        SimpleSecretTenantLineHandler handler =
                new SimpleSecretTenantLineHandler(context, new TenantProperties());

        assertThat(handler.ignoreTable("orders")).isFalse();
        assertThatThrownBy(handler::getTenantId)
                .isInstanceOf(TenantException.class);
    }

    @Test
    void shouldRejectExplicitTenantColumnOnInsert() {
        SimpleSecretTenantLineHandler handler = new SimpleSecretTenantLineHandler(
                new TenantContext(() -> "tenant-a"), new TenantProperties());

        assertThatThrownBy(() -> handler.ignoreInsert(
                List.of(new Column("id"), new Column("tenant_id")), "tenant_id"))
                .isInstanceOf(TenantException.class)
                .hasMessageContaining("INSERT")
                .hasMessageContaining("tenant_id");
    }
}

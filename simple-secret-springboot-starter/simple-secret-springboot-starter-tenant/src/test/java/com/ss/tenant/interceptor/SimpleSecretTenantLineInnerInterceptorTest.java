package com.ss.tenant.interceptor;

import com.ss.tenant.config.TenantProperties;
import com.ss.tenant.context.TenantContext;
import com.ss.tenant.exception.TenantException;
import com.ss.tenant.handler.SimpleSecretTenantLineHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证租户 SQL 插件对写入路径采用失败关闭策略。 */
class SimpleSecretTenantLineInnerInterceptorTest {

    private final TenantContext context = new TenantContext(() -> "tenant-a");
    private final SimpleSecretTenantLineInnerInterceptor interceptor =
            new SimpleSecretTenantLineInnerInterceptor(
                    new SimpleSecretTenantLineHandler(context, new TenantProperties()));

    @Test
    void shouldInjectTenantIntoOrdinaryInsert() {
        String sql = interceptor.parserMulti(
                "insert into tenant_orders (id, name) values (1, 'alpha')", null);

        assertThat(sql).containsIgnoringCase("tenant_id").contains("'tenant-a'");
    }

    @Test
    void shouldRejectInsertWithoutColumnList() {
        assertThatThrownBy(() -> interceptor.parserMulti(
                "insert into tenant_orders values (1, 'tenant-b', 'gamma')", null))
                .isInstanceOf(TenantException.class)
                .hasMessageContaining("column list");
    }

    @Test
    void shouldRejectExplicitTenantColumnInsert() {
        assertThatThrownBy(() -> interceptor.parserMulti(
                "insert into tenant_orders (id, tenant_id, name) "
                        + "values (1, 'tenant-b', 'gamma')", null))
                .isInstanceOf(TenantException.class)
                .hasMessageContaining("INSERT");
    }

    @Test
    void shouldRejectTenantColumnUpdate() {
        assertThatThrownBy(() -> interceptor.parserMulti(
                "update tenant_orders set tenant_id = 'tenant-b' where id = 1", null))
                .isInstanceOf(TenantException.class)
                .hasMessageContaining("UPDATE");
    }

    @Test
    void shouldAllowTrustedTenantColumnUpdateInsideIgnoreScope() {
        String sql = context.callWithoutTenant(() -> interceptor.parserMulti(
                "update tenant_orders set tenant_id = 'tenant-b' where id = 1", null));

        assertThat(sql).containsIgnoringCase("set tenant_id = 'tenant-b'")
                .doesNotContain("tenant-a");
    }
}

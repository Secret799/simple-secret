package com.ss.tenant.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 验证租户配置默认值和 SQL 标识符边界。 */
class TenantPropertiesTest {

    @Test
    void shouldProvideSafeDefaults() {
        TenantProperties properties = new TenantProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getColumn()).isEqualTo("tenant_id");
        assertThat(properties.getExcludedTables()).isEmpty();
        assertThat(properties.isExcludedTable("orders")).isFalse();
    }

    @Test
    void shouldMatchExcludedTablesCaseInsensitively() {
        TenantProperties properties = new TenantProperties();
        properties.setExcludedTables(Set.of(" Audit_Log ", "system_config"));

        assertThat(properties.isExcludedTable("audit_log")).isTrue();
        assertThat(properties.isExcludedTable("SYSTEM_CONFIG")).isTrue();
        assertThat(properties.isExcludedTable("orders")).isFalse();

        properties.setExcludedTables(null);
        assertThat(properties.getExcludedTables()).isEmpty();
    }

    @Test
    void shouldRejectUnsafeTenantColumns() {
        TenantProperties properties = new TenantProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> properties.setColumn(""));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setColumn("t.tenant_id"));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setColumn("tenant_id --"));
        assertThatIllegalArgumentException().isThrownBy(() -> properties.setColumn("1tenant"));

        properties.setColumn("account_id");
        assertThat(properties.getColumn()).isEqualTo("account_id");
    }
}

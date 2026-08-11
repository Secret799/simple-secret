package com.ss.tenant.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.ss.mybatis.domain.BaseEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证租户实体只扩展持久化基础实体的租户标识。 */
class TenantEntityTest {

    @Test
    void shouldExposeTenantIdOnBaseEntity() {
        TenantEntity entity = new TenantEntity();

        entity.setTenantId("tenant-a");

        assertThat(entity).isInstanceOf(BaseEntity.class);
        assertThat(entity.getTenantId()).isEqualTo("tenant-a");
    }

    @Test
    void shouldExcludeTenantIdFromGeneratedInsertAndUpdateStatements() throws Exception {
        Field tenantId = TenantEntity.class.getDeclaredField("tenantId");
        TableField mapping = tenantId.getAnnotation(TableField.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.insertStrategy()).isEqualTo(FieldStrategy.NEVER);
        assertThat(mapping.updateStrategy()).isEqualTo(FieldStrategy.NEVER);
    }
}

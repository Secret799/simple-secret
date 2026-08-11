package com.ss.mybatis.audit;

import com.ss.mybatis.domain.BaseEntity;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证审计字段填充不依赖认证 starter。 */
class SimpleSecretMetaObjectHandlerTest {
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Test
    void shouldFillMissingInsertFieldsFromConsumerContext() {
        TestEntity entity = new TestEntity();
        SimpleSecretMetaObjectHandler handler = new SimpleSecretMetaObjectHandler(
                () -> new AuditContext(7L, 3L),
                Clock.fixed(NOW, ZoneOffset.UTC));

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateBy()).isEqualTo(7L);
        assertThat(entity.getUpdateBy()).isEqualTo(7L);
        assertThat(entity.getCreateDept()).isEqualTo(3L);
        assertThat(entity.getCreateTime()).isEqualTo(LOCAL_NOW);
        assertThat(entity.getUpdateTime()).isEqualTo(LOCAL_NOW);
    }

    @Test
    void shouldPreserveExplicitInsertFields() {
        TestEntity entity = new TestEntity();
        LocalDateTime explicit = LOCAL_NOW.minusDays(1L);
        entity.setCreateBy(11L);
        entity.setCreateDept(12L);
        entity.setCreateTime(explicit);
        entity.setUpdateBy(13L);
        entity.setUpdateTime(explicit);

        new SimpleSecretMetaObjectHandler(
                () -> new AuditContext(7L, 3L), Clock.fixed(NOW, ZoneOffset.UTC))
                .insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateBy()).isEqualTo(11L);
        assertThat(entity.getCreateDept()).isEqualTo(12L);
        assertThat(entity.getCreateTime()).isEqualTo(explicit);
        assertThat(entity.getUpdateBy()).isEqualTo(13L);
        assertThat(entity.getUpdateTime()).isEqualTo(explicit);
    }

    @Test
    void shouldRefreshUpdateFieldsWithoutRequiringContext() {
        TestEntity entity = new TestEntity();
        entity.setUpdateBy(9L);
        entity.setUpdateTime(LOCAL_NOW.minusDays(1L));

        new SimpleSecretMetaObjectHandler(AuditContextProvider.empty(),
                Clock.fixed(NOW, ZoneOffset.UTC))
                .updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdateBy()).isEqualTo(9L);
        assertThat(entity.getUpdateTime()).isEqualTo(LOCAL_NOW);
    }

    @Test
    void shouldReplaceUpdateActorWhenContextIsAvailable() {
        TestEntity entity = new TestEntity();
        entity.setUpdateBy(9L);

        new SimpleSecretMetaObjectHandler(
                () -> new AuditContext(7L, null), Clock.fixed(NOW, ZoneOffset.UTC))
                .updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdateBy()).isEqualTo(7L);
    }

    private static final class TestEntity extends BaseEntity {
        private static final long serialVersionUID = 1L;
    }
}

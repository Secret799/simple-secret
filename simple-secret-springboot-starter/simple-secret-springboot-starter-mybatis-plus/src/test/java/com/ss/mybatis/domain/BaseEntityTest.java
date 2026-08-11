package com.ss.mybatis.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证基础实体仅包含持久化审计字段。 */
class BaseEntityTest {

    @Test
    void shouldExposeAuditingFieldsWithMybatisFillMetadata() throws Exception {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateDept(3L);
        entity.setCreateBy(7L);
        entity.setCreateTime(now);
        entity.setUpdateBy(8L);
        entity.setUpdateTime(now);

        assertThat(entity.getCreateDept()).isEqualTo(3L);
        assertThat(entity.getCreateBy()).isEqualTo(7L);
        assertThat(entity.getCreateTime()).isEqualTo(now);
        assertThat(entity.getUpdateBy()).isEqualTo(8L);
        assertThat(entity.getUpdateTime()).isEqualTo(now);
        assertFill("createTime", FieldFill.INSERT);
        assertFill("updateTime", FieldFill.INSERT_UPDATE);
        assertThat(BaseEntity.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("params");
    }

    private static void assertFill(String fieldName, FieldFill expected) throws Exception {
        TableField annotation = BaseEntity.class.getDeclaredField(fieldName)
                .getAnnotation(TableField.class);
        assertThat(annotation.fill()).isEqualTo(expected);
    }

    private static final class TestEntity extends BaseEntity {
        private static final long serialVersionUID = 1L;
    }
}

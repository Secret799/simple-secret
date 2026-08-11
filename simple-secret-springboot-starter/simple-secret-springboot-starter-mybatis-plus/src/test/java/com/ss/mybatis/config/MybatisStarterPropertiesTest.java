package com.ss.mybatis.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** 验证 MyBatis-Plus starter 配置默认值和边界。 */
class MybatisStarterPropertiesTest {

    @Test
    void shouldUseSafeDefaults() {
        MybatisStarterProperties properties = new MybatisStarterProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isPaginationEnabled()).isTrue();
        assertThat(properties.isOptimisticLockerEnabled()).isTrue();
        assertThat(properties.getMaxPageSize()).isEqualTo(500L);
        assertThat(properties.isOverflow()).isFalse();
    }

    @Test
    void shouldRejectNonPositiveMaxPageSize() {
        MybatisStarterProperties properties = new MybatisStarterProperties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setMaxPageSize(0L))
                .withMessage("maxPageSize must be greater than zero");
    }
}

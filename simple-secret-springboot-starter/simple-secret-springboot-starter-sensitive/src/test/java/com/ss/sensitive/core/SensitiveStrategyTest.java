package com.ss.sensitive.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证迁移后的脱敏输出与 Honeybee 已使用的规则兼容。 */
class SensitiveStrategyTest {

    @Test
    void shouldMaskSupportedSensitiveValues() {
        assertThat(SensitiveStrategy.ID_CARD.mask("11010519491231002X"))
                .isEqualTo("110***********002X");
        assertThat(SensitiveStrategy.PHONE.mask("18049531999"))
                .isEqualTo("180****1999");
        assertThat(SensitiveStrategy.ADDRESS.mask("北京市海淀区马连洼街道289号"))
                .isEqualTo("北京市海淀区马********");
        assertThat(SensitiveStrategy.EMAIL.mask("duandazhi-jack@gmail.com.cn"))
                .isEqualTo("d*************@gmail.com.cn");
        assertThat(SensitiveStrategy.BANK_CARD.mask("11011111222233333256"))
                .isEqualTo("1101 **** **** **** 3256");
    }

    @Test
    void shouldHandleBlankAndShortValuesWithoutLeakingOrThrowing() {
        for (SensitiveStrategy strategy : SensitiveStrategy.values()) {
            assertThat(strategy.mask("")).isEmpty();
        }
        assertThat(SensitiveStrategy.ID_CARD.mask("123456")).isEmpty();
        assertThat(SensitiveStrategy.BANK_CARD.mask("1234 567")).isEqualTo("1234567");
    }
}

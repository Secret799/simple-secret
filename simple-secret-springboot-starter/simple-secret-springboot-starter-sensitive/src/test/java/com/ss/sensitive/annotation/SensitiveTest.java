package com.ss.sensitive.annotation;

import com.ss.sensitive.core.SensitiveStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证脱敏注解只携带稳定的字段序列化契约。 */
class SensitiveTest {

    @Test
    void shouldExposeStrategyAndAuthorizationHintsAtRuntime() throws Exception {
        Field field = Sample.class.getDeclaredField("phone");
        Sensitive sensitive = field.getAnnotation(Sensitive.class);

        assertThat(sensitive.strategy()).isEqualTo(SensitiveStrategy.PHONE);
        assertThat(sensitive.roleKey()).isEqualTo("auditor");
        assertThat(sensitive.perms()).isEqualTo("customer:read:raw");
    }

    private static final class Sample {
        @Sensitive(
                strategy = SensitiveStrategy.PHONE,
                roleKey = "auditor",
                perms = "customer:read:raw")
        private String phone;
    }
}

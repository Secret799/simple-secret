package com.ss.idempotent.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证重复提交注解兼容 Honeybee 的默认时间窗口。 */
class RepeatSubmitTest {

    @Test
    void shouldExposeRuntimeDefaults() throws Exception {
        Method method = Sample.class.getDeclaredMethod("submit");
        RepeatSubmit annotation = method.getAnnotation(RepeatSubmit.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.interval()).isEqualTo(5000);
        assertThat(annotation.timeUnit()).isEqualTo(TimeUnit.MILLISECONDS);
        assertThat(annotation.message()).isEqualTo("{repeat.submit.message}");
        assertThat(annotation.releaseOnException()).isTrue();
    }

    private static class Sample {

        @RepeatSubmit
        void submit() {
        }
    }
}

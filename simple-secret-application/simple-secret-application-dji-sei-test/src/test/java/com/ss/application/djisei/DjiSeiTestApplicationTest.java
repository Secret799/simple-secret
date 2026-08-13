package com.ss.application.djisei;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DJI SEI 测试应用默认启动测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
@SpringBootTest(classes = DjiSeiTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DjiSeiTestApplicationTest {

    /** Spring 环境配置。 */
    @Autowired
    private Environment environment;

    /** 当前应用上下文。 */
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldStartWithoutNativeMediaLibraryByDefault() {
        assertThat(environment.getProperty("simple-secret.zlm4j.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("simple-secret.easymedia.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty(
                "simple-secret.easymedia.management-api-enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("simple-secret.dji-sei.enabled", Boolean.class)).isFalse();
        assertThat(applicationContext.containsBean("zlmMediaContext")).isFalse();
    }
}

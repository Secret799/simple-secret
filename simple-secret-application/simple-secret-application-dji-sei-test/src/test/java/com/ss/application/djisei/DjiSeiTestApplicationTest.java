package com.ss.application.djisei;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DJI SEI 测试应用默认启动测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class DjiSeiTestApplicationTest {

    @Test
    void shouldStartWithoutNativeMediaLibraryByDefault() {
        SpringApplication application = new SpringApplication(DjiSeiTestApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run()) {
            Environment environment = context.getEnvironment();

            assertThat(environment.getProperty("simple-secret.zlm4j.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("simple-secret.easymedia.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty(
                    "simple-secret.easymedia.management-api-enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("simple-secret.dji-sei.enabled", Boolean.class)).isFalse();
            assertThat(context.containsBean("zlmMediaContext")).isFalse();
        }
    }
}

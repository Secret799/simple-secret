package com.ss.application.pushstream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 推流应用默认启动测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
@SpringBootTest(classes = PublishStreamApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PublishStreamApplicationTest {

    /** Spring 配置环境。 */
    @Autowired
    private Environment environment;

    /** Spring 应用上下文。 */
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldStartWithoutNativeLibraryOrFfmpegByDefault() {
        assertThat(environment.getProperty("simple-secret.zlm4j.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("simple-secret.publish-stream.enabled", Boolean.class)).isFalse();
        assertThat(applicationContext.containsBean("zlmMediaContext")).isFalse();
        assertThat(applicationContext.containsBean("publishStreamScheduler")).isFalse();
        assertThat(applicationContext.containsBean("ffmpegProcessManager")).isFalse();
    }
}

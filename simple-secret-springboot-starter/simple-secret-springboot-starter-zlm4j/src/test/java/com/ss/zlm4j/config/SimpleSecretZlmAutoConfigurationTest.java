package com.ss.zlm4j.config;

import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.context.ZlmCallbackHandlerContext;
import com.ss.zlm4j.context.ZlmMediaContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SimpleSecretZlmAutoConfiguration} 冒烟测试。
 *
 * <p>注意：不会验证默认开启的场景——{@link ZlmMediaContext#initMediaServer()} 会加载
 * ZLMediaKit 原生库 {@code mk_api}，无原生库环境直接失败。这里只验证开关关闭时
 * 不会创建任何需要原生库的 Bean，以及属性绑定仍可用。</p>
 */
class SimpleSecretZlmAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretZlmAutoConfiguration.class));

    @Test
    void shouldBeDisabledByDefaultAndExposeCompleteSafeDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ZlmMediaProperties.class);
            assertThat(context).doesNotHaveBean(ZlmMediaContext.class);
            assertThat(context).doesNotHaveBean(ZlmCallbackHandlerContext.class);

            ZlmMediaProperties properties = context.getBean(ZlmMediaProperties.class);
            assertThat(properties.getEnabled()).isFalse();
            assertThat(properties.getThreadNum()).isPositive();
            assertThat(properties.getHttpPort()).isPositive();
            assertThat(properties.getRtspPort()).isPositive();
            assertThat(properties.getRtmpPort()).isPositive();
            assertThat(properties.getRtcPort()).isPositive();
            assertThat(properties.getLogLevel()).isNotNull();
            assertThat(properties.getLogMask()).isNotNull();
            assertThat(properties.getLogFileDays()).isNotNull();
        });
    }

    @Test
    void canBeDisabledWithoutLoadingNativeLibrary() {
        runner.withPropertyValues("simple-secret.zlm4j.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ZlmMediaProperties.class);
                    assertThat(context).hasSingleBean(ScheduledExecutorService.class);
                    assertThat(context).doesNotHaveBean(ZlmMediaContext.class);
                    assertThat(context).doesNotHaveBean(ZlmCallbackHandlerContext.class);
                });
    }

    @Test
    void bindsPropertiesFromPropertySource() {
        runner.withPropertyValues(
                        "simple-secret.zlm4j.enabled=false",
                        "simple-secret.zlm4j.http-port=7081")
                .run(context -> {
                    ZlmMediaProperties properties = context.getBean(ZlmMediaProperties.class);
                    assertThat(properties.getHttpPort()).isEqualTo(7081);
                });
    }
}

package com.ss.camerazlm.config;

import com.ss.camerazlm.DahuaZlmStreamService;
import com.ss.easymedia.h264.H264NakedFlowPushZlmManager;
import com.ss.ics.dahua.DahuaCameraSdkService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.objenesis.ObjenesisStd;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Camera-to-ZLM 自动配置开关和前置条件测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class SimpleSecretCameraZlmAutoConfigurationTest {

    /** 自动配置测试运行器。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretCameraZlmAutoConfiguration.class,
                    DahuaZlmStreamAutoConfiguration.class));

    @Test
    void staysDisabledByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(DahuaZlmStreamService.class));
    }

    @Test
    void doesNotCreateServiceWithoutExplicitCameraSdkBean() {
        contextRunner
                .withPropertyValues(
                        "simple-secret.camera-zlm.enabled=true",
                        "simple-secret.zlm4j.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DahuaZlmStreamService.class);
                    assertThat(context).doesNotHaveBean("cameraZlmH264Publisher");
                });
    }

    @Test
    void staysDisabledWhenZlmIsDisabled() {
        contextRunner
                .withPropertyValues("simple-secret.camera-zlm.enabled=true")
                .run(context ->
                        assertThat(context).doesNotHaveBean(DahuaZlmStreamService.class));
    }

    @Test
    void doesNotReuseUnqualifiedHostPublisher() {
        ObjenesisStd objenesis = new ObjenesisStd();
        contextRunner
                .withPropertyValues(
                        "simple-secret.camera-zlm.enabled=true",
                        "simple-secret.zlm4j.enabled=true")
                .withBean(DahuaCameraSdkService.class,
                        () -> objenesis.newInstance(DahuaCameraSdkService.class),
                        definition -> definition.setDestroyMethodName(""))
                .withBean(H264NakedFlowPushZlmManager.class,
                        () -> objenesis.newInstance(H264NakedFlowPushZlmManager.class),
                        definition -> definition.setDestroyMethodName(""))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DahuaZlmStreamService.class);
                    assertThat(context).doesNotHaveBean("cameraZlmH264Publisher");
                });
    }

    @Test
    void createsServiceWithExplicitDedicatedPublisher() {
        ObjenesisStd objenesis = new ObjenesisStd();
        contextRunner
                .withPropertyValues(
                        "simple-secret.camera-zlm.enabled=true",
                        "simple-secret.zlm4j.enabled=true")
                .withBean(DahuaCameraSdkService.class,
                        () -> objenesis.newInstance(DahuaCameraSdkService.class),
                        definition -> definition.setDestroyMethodName(""))
                .withBean("cameraZlmH264Publisher", H264NakedFlowPushZlmManager.class,
                        () -> objenesis.newInstance(H264NakedFlowPushZlmManager.class),
                        definition -> definition.setDestroyMethodName(""))
                .run(context ->
                        assertThat(context).hasSingleBean(DahuaZlmStreamService.class));
    }
}

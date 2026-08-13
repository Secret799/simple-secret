package com.ss.application.djisei.config;

import com.ss.application.djisei.diagnostic.DjiSeiTrackCallback;
import com.ss.easymedia.config.properties.EmsProperties;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.context.ZlmMediaContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DJI SEI 本地配置档案绑定测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class DjiSeiLocalProfileTest {

    @Test
    void shouldBindRtmpOnlyLocalDiagnosticProfileWithoutNativeLibrary() {
        SpringApplication application = new SpringApplication(LocalProfileConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("local");
        try (ConfigurableApplicationContext context = application.run()) {
            ZlmMediaProperties zlm = context.getBean(ZlmMediaProperties.class);
            EmsProperties easyMedia = context.getBean(EmsProperties.class);
            DjiSeiProperties djiSei = context.getBean(DjiSeiProperties.class);

            assertThat(zlm.getEnabled()).isTrue();
            assertThat(zlm.getListenIp()).isEqualTo("0.0.0.0");
            assertThat(zlm.getRtmpPort()).isEqualTo(7935);
            assertThat(zlm.getHttpListenerEnabled()).isFalse();
            assertThat(zlm.getRtspListenerEnabled()).isFalse();
            assertThat(zlm.getRtmpListenerEnabled()).isTrue();
            assertThat(zlm.getRtcListenerEnabled()).isFalse();
            assertThat(zlm.getEnableRtmp()).isEqualTo(1);
            assertThat(zlm.getAllowAnonymousPublish()).isTrue();
            assertThat(zlm.getAllowAnonymousPlay()).isFalse();
            assertThat(zlm.getRootPath()).isEqualTo("./runtime/dji-sei");
            assertThat(zlm.getLogPath()).isEqualTo("./runtime/dji-sei/logs");
            assertThat(easyMedia.isEnabled()).isTrue();
            assertThat(easyMedia.isManagementApiEnabled()).isFalse();
            assertThat(easyMedia.getWebrtc().isEnabled()).isFalse();
            assertThat(djiSei.isEnabled()).isTrue();
            assertThat(context.getBeansOfType(DjiSeiTrackCallback.class)).hasSize(1);
            assertThat(context.getBeansOfType(ZlmMediaContext.class)).isEmpty();
        }
    }

    /**
     * 仅加载属性绑定与应用自有回调配置，排除原生媒体自动配置。
     *
     * @author junpzx
     * @since 2026-08-13
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ZlmMediaProperties.class, EmsProperties.class})
    static class LocalProfileConfiguration extends DjiSeiConfiguration {
    }
}

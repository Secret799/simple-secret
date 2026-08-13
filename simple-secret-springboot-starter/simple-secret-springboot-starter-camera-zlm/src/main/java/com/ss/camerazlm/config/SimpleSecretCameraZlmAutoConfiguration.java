package com.ss.camerazlm.config;

import com.ss.easymedia.h264.H264NakedFlowPushZlmManager;
import com.ss.ics.dahua.DahuaCameraSdkService;
import com.ss.zlm4j.config.SimpleSecretZlmAutoConfiguration;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.context.ZlmMediaContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 大华 Camera SDK 到 ZLM 的显式启用自动配置。
 *
 * @author junpzx
 * @since 2026-08-13
 */
@AutoConfiguration
@AutoConfigureAfter(SimpleSecretZlmAutoConfiguration.class)
@EnableConfigurationProperties(CameraZlmProperties.class)
@ConditionalOnProperty(name = {
        "simple-secret.camera-zlm.enabled",
        "simple-secret.zlm4j.enabled"
}, havingValue = "true")
public class SimpleSecretCameraZlmAutoConfiguration {

    /**
     * 创建当前适配层专用的 H.264 publisher。
     *
     * @param zlmMediaProperties ZLM 媒体配置
     * @param zlmMediaContext 已完成初始化的 ZLM 原生上下文
     * @return 随 Spring 生命周期关闭的 H.264 publisher
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean({ZlmMediaContext.class, DahuaCameraSdkService.class})
    @ConditionalOnMissingBean(name = "cameraZlmH264Publisher")
    public H264NakedFlowPushZlmManager cameraZlmH264Publisher(
            ZlmMediaProperties zlmMediaProperties,
            ZlmMediaContext zlmMediaContext) {
        return new H264NakedFlowPushZlmManager(zlmMediaProperties);
    }

}

package com.ss.camerazlm.config;

import com.ss.camerazlm.DahuaZlmStreamService;
import com.ss.easymedia.h264.H264NakedFlowPushZlmManager;
import com.ss.ics.dahua.DahuaCameraSdkService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 在 H.264 publisher 就绪后装配大华转推服务。
 *
 * @author junpzx
 * @since 2026-08-13
 */
@AutoConfiguration
@AutoConfigureAfter(SimpleSecretCameraZlmAutoConfiguration.class)
@ConditionalOnProperty(name = {
        "simple-secret.camera-zlm.enabled",
        "simple-secret.zlm4j.enabled"
}, havingValue = "true")
public class DahuaZlmStreamAutoConfiguration {

    /**
     * 创建大华实时 H.264 转推服务。
     *
     * @param cameraService 由宿主使用受控 native 路径创建的大华 SDK 服务
     * @param publisher H.264 publisher
     * @param properties 有界队列和关闭超时配置
     * @return 大华到 ZLM 的转推服务
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(value = DahuaCameraSdkService.class, name = "cameraZlmH264Publisher")
    @ConditionalOnMissingBean(DahuaZlmStreamService.class)
    public DahuaZlmStreamService dahuaZlmStreamService(
            DahuaCameraSdkService cameraService,
            @Qualifier("cameraZlmH264Publisher") H264NakedFlowPushZlmManager publisher,
            CameraZlmProperties properties) {
        return new DahuaZlmStreamService(
                cameraService, publisher,
                properties.getQueueCapacity(), properties.getMaxFrameBytes(),
                properties.getMaxBufferedBytes(), properties.getCloseTimeout());
    }
}

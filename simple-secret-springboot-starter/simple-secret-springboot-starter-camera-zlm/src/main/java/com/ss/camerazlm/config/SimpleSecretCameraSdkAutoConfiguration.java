package com.ss.camerazlm.config;

import com.ss.camerazlm.config.properties.CameraZlmProperties;
import com.ss.ics.dahua.DahuaCameraSdkService;
import com.ss.ics.registry.CameraSdkServiceRegistry;
import com.ss.ics.service.CameraSdkService;
import com.ss.zlm4j.context.ZlmMediaContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 相机 sdk 自动注册配置器
 *
 * @author JunPzx
 * @since 2026/9/17 14:27
 */
@AutoConfiguration
@EnableConfigurationProperties(CameraZlmProperties.class)
public class SimpleSecretCameraSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CameraSdkServiceRegistry.class)
    public CameraSdkServiceRegistry cameraSdkServiceRegistry(
            ObjectProvider<CameraSdkService> services) {
        return new CameraSdkServiceRegistry(services.orderedStream().toList());
    }


}

package com.ss.ics.hikvision.config;

import com.ss.ics.hikvision.HikvisionCameraSdkService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Objects;

/** 海康威视 Camera SDK 自动配置。 */
@AutoConfiguration
@ConditionalOnClass(HikvisionCameraSdkService.class)
@ConditionalOnProperty(prefix = "simple-secret.camera-sdk.hikvision", name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(HikvisionCameraSdkProperties.class)
public class HikvisionCameraSdkAutoConfiguration {

    /** @return 默认海康威视 SDK 服务工厂 */
    @Bean
    @ConditionalOnMissingBean
    HikvisionCameraSdkServiceFactory hikvisionCameraSdkServiceFactory() {
        return HikvisionCameraSdkService::open;
    }

    /**
     * 创建由 Spring 管理关闭生命周期的海康威视 SDK 服务。
     *
     * @param factory SDK 服务工厂
     * @param properties SDK 配置
     * @return 海康威视 SDK 服务
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    HikvisionCameraSdkService hikvisionCameraSdkService(
            HikvisionCameraSdkServiceFactory factory,
            HikvisionCameraSdkProperties properties) {
        return Objects.requireNonNull(factory.create(properties.toOptions()),
                "HikvisionCameraSdkServiceFactory must not return null");
    }
}

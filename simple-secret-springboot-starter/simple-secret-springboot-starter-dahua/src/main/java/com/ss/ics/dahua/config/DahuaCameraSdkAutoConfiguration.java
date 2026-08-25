package com.ss.ics.dahua.config;

import com.ss.ics.dahua.DahuaCameraSdkService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Objects;

/** 大华 Camera SDK 自动配置。 */
@AutoConfiguration
@ConditionalOnClass(DahuaCameraSdkService.class)
@ConditionalOnProperty(prefix = "simple-secret.camera-sdk.dahua", name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(DahuaCameraSdkProperties.class)
public class DahuaCameraSdkAutoConfiguration {

    /** @return 默认大华 SDK 服务工厂 */
    @Bean
    @ConditionalOnMissingBean
    DahuaCameraSdkServiceFactory dahuaCameraSdkServiceFactory() {
        return DahuaCameraSdkService::open;
    }

    /**
     * 创建由 Spring 管理关闭生命周期的大华 SDK 服务。
     *
     * @param factory SDK 服务工厂
     * @param properties SDK 配置
     * @return 大华 SDK 服务
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    DahuaCameraSdkService dahuaCameraSdkService(
            DahuaCameraSdkServiceFactory factory,
            DahuaCameraSdkProperties properties) {
        return Objects.requireNonNull(factory.create(properties.toOptions()),
                "DahuaCameraSdkServiceFactory must not return null");
    }
}

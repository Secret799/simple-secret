package com.ss.magicapi.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Simple Secret Magic API 启用控制和启动前安全校验。
 */
@AutoConfiguration(beforeName = MagicApiAutoConfigurationImportFilter.MAGIC_API_AUTO_CONFIGURATION)
@ConditionalOnClass(name = MagicApiAutoConfigurationImportFilter.MAGIC_API_AUTO_CONFIGURATION)
@ConditionalOnProperty(prefix = "simple-secret.magic-api", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MagicApiStarterProperties.class)
public class SimpleSecretMagicApiAutoConfiguration {

    /**
     * 在创建任何 Magic API 单例之前校验外部资源和编辑器配置。
     *
     * @param environment Spring 环境
     * @return 配置校验后处理器
     */
    @Bean
    static BeanFactoryPostProcessor magicApiConfigurationValidationPostProcessor(
            Environment environment) {
        return beanFactory -> MagicApiConfigurationValidator.validate(environment);
    }
}

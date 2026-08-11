package com.ss.core.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Core starter 的基础属性自动配置。 */
@AutoConfiguration
@EnableConfigurationProperties(CoreProperties.class)
public class SimpleSecretCoreAutoConfiguration {
}

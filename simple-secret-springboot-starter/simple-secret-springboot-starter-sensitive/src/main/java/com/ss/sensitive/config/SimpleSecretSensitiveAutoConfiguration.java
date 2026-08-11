package com.ss.sensitive.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.sensitive.core.SensitiveService;
import com.ss.sensitive.jackson.SimpleSecretSensitiveModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Simple Secret Jackson 字段脱敏自动配置。 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
@ConditionalOnClass({ObjectMapper.class, SimpleSecretSensitiveModule.class})
@ConditionalOnProperty(
        prefix = "simple-secret.sensitive",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SimpleSecretSensitiveAutoConfiguration {

    /**
     * 提供默认失败关闭的脱敏决策。
     *
     * @return 始终要求脱敏的服务
     */
    @Bean
    @ConditionalOnMissingBean
    SensitiveService sensitiveService() {
        return SensitiveService.alwaysMask();
    }

    /**
     * 创建由 Spring Boot 自动安装到 ObjectMapper 的脱敏模块。
     *
     * @param sensitiveService 脱敏决策服务
     * @return Jackson 脱敏模块
     */
    @Bean
    @ConditionalOnMissingBean
    SimpleSecretSensitiveModule simpleSecretSensitiveModule(
            SensitiveService sensitiveService) {
        return new SimpleSecretSensitiveModule(sensitiveService);
    }
}

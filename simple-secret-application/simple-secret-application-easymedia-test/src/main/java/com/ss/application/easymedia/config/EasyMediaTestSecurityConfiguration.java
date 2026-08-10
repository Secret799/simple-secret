package com.ss.application.easymedia.config;

import com.ss.easymedia.security.EasyMediaManagementAuthorizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EasyMedia 测试管理接口安全配置。
 */
@Configuration(proxyBeanMethods = false)
public class EasyMediaTestSecurityConfiguration {

    /**
     * 管理 API 开启时使用固定请求头令牌进行本地测试授权。
     *
     * @param token 环境配置的测试令牌
     * @return 管理接口授权器
     */
    @Bean
    @ConditionalOnProperty(prefix = "simple-secret.easymedia",
            name = "management-api-enabled", havingValue = "true")
    EasyMediaManagementAuthorizer easyMediaTestManagementAuthorizer(
            @Value("${simple-secret.test.easymedia.management-token:}") String token) {
        return new TestTokenManagementAuthorizer(token);
    }
}

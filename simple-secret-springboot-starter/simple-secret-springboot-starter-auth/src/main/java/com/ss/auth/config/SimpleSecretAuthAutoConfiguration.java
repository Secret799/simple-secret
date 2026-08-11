package com.ss.auth.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.ss.auth.service.AuthStrategy;
import com.ss.auth.service.ClientService;
import com.ss.auth.strategy.AuthStrategyRegistry;
import com.ss.auth.support.LoginHelper;
import com.ss.auth.support.LoginUserStpInterface;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/** Simple Secret 认证 starter 的默认自动配置。 */
@AutoConfiguration
@ConditionalOnClass(StpLogic.class)
@ConditionalOnProperty(prefix = "simple-secret.auth", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AuthProperties.class)
public class SimpleSecretAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    StpLogic simpleSecretStpLogic() {
        return StpUtil.getStpLogic();
    }

    @Bean
    @ConditionalOnMissingBean
    LoginHelper simpleSecretLoginHelper(StpLogic stpLogic) {
        return new LoginHelper(stpLogic);
    }

    @Bean
    @ConditionalOnMissingBean(StpInterface.class)
    StpInterface simpleSecretStpInterface(LoginHelper loginHelper) {
        return new LoginUserStpInterface(loginHelper);
    }

    @Bean
    @ConditionalOnBean(ClientService.class)
    @ConditionalOnMissingBean
    AuthStrategyRegistry simpleSecretAuthStrategyRegistry(
            List<AuthStrategy> strategies, ClientService clientService) {
        return new AuthStrategyRegistry(clientService, strategies);
    }
}
